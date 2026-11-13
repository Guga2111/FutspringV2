package com.futspring.backend.domain.file;

import com.futspring.backend.shared.exception.AppException;
import com.twelvemonkeys.contrib.exif.EXIFUtilities;
import com.twelvemonkeys.contrib.exif.Orientation;
import com.twelvemonkeys.contrib.tiff.TIFFUtilities;
import org.springframework.http.HttpStatus;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

/**
 * Decodes an uploaded image from its content, never from what the client declares: the public uploads
 * ({@link FileUploadService}) and the private finance images share the format, type and pixel checks.
 * <p>
 * Memory: a 5 MB file can hold 20 million pixels (~80 MB decoded), so the pixels are never decoded at full size. The
 * reader skips pixels while reading (source subsampling) down to at most twice the size asked for, then the image is
 * scaled smoothly to it: a 20-Mpx photo asked at 2048 px peaks at ~20 MB instead of ~240 MB. And at most
 * {@link #MAX_CONCURRENT} images are processed at once in the whole server ({@link #withPermit}).
 */
public final class ImageDecoder {

    /** Ceiling checked from the header, before the pixels are decoded (~80 MB as ARGB). */
    public static final long MAX_PIXELS = 20_000_000;

    /**
     * Images decoded and re-encoded at the same time, across every request (each holds up to ~70 MB). A request over
     * the limit is refused at once instead of waiting: the callers run inside a transaction, so a waiting upload
     * would hold one of the 10 database connections.
     */
    static final int MAX_CONCURRENT = 2;
    private static final Semaphore PERMITS = new Semaphore(MAX_CONCURRENT);

    public enum Format {
        JPEG("image/jpeg"), PNG("image/png"), WEBP("image/webp");

        private final String contentType;

        Format(String contentType) {
            this.contentType = contentType;
        }

        public String contentType() {
            return contentType;
        }

        static Format fromReaderName(String name) {
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "jpeg", "jpg" -> JPEG;
                case "png" -> PNG;
                case "webp" -> WEBP;
                default -> null;
            };
        }
    }

    /**
     * The decoded pixels, at most {@code maxSide} on the longest side, upright when the file carried an EXIF
     * orientation; {@code TYPE_INT_ARGB} when the image has transparency, else {@code TYPE_INT_RGB} once scaled.
     */
    public record Decoded(Format format, BufferedImage image) {}

    private ImageDecoder() {}

    /** Runs the work (decode and re-encode) holding one of the server's image permits; 503 when none is free. */
    public static <T> T withPermit(Supplier<T> work) {
        if (!PERMITS.tryAcquire()) {
            throw new AppException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Muitas imagens sendo enviadas agora. Tente de novo em instantes.");
        }
        try {
            return work.get();
        } finally {
            PERMITS.release();
        }
    }

    public static Decoded decode(byte[] bytes, String declaredType, Set<Format> allowed, String unsupportedMessage,
                                 int maxSide) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw bad("Imagem inválida");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                Format format = Format.fromReaderName(reader.getFormatName());
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                long pixels = (long) width * height;
                if (pixels > MAX_PIXELS || pixels <= 0) {
                    throw bad("A imagem deve ter até 20 milhões de pixels");
                }
                if (format == null || !allowed.contains(format)) {
                    throw bad(unsupportedMessage);
                }
                if (!format.contentType().equals(declaredType)) {
                    throw bad("O conteúdo da imagem não corresponde ao tipo informado");
                }
                // every factor-th pixel of every factor-th row: never more than 2 × maxSide on the long side in memory
                int factor = Math.max(1, (int) Math.ceil(Math.max(width, height) / (2.0 * maxSide)));
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(factor, factor, 0, 0);
                BufferedImage image = reader.read(0, param);
                if (image == null) {
                    throw bad("Imagem inválida");
                }
                image = fit(image, maxSide);
                Orientation orientation = EXIFUtilities.findImageOrientation(reader.getImageMetadata(0));
                if (orientation != null && orientation != Orientation.Normal) {
                    image = TIFFUtilities.applyOrientation(image, orientation.value());
                }
                return new Decoded(format, image);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof AppException app) {
                throw app;
            }
            // Truncated or malformed files make some readers throw unchecked exceptions too
            throw bad("Imagem inválida");
        }
    }

    // Scaled smoothly to fit maxSide (the subsampled image is at most twice as large, so one bilinear pass is enough)
    private static BufferedImage fit(BufferedImage image, int maxSide) {
        int longest = Math.max(image.getWidth(), image.getHeight());
        if (longest <= maxSide) return image;
        double scale = (double) maxSide / longest;
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        int type = image.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage scaled = new BufferedImage(width, height, type);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private static AppException bad(String message) {
        return new AppException(HttpStatus.BAD_REQUEST, message);
    }
}

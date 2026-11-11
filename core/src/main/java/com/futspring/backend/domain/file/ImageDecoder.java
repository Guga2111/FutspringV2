package com.futspring.backend.domain.file;

import com.futspring.backend.shared.exception.AppException;
import com.twelvemonkeys.contrib.exif.EXIFUtilities;
import com.twelvemonkeys.contrib.exif.Orientation;
import com.twelvemonkeys.contrib.tiff.TIFFUtilities;
import org.springframework.http.HttpStatus;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

/**
 * Decodes an uploaded image from its content, never from what the client declares: the public uploads
 * ({@link FileUploadService}) and the private finance images share the format, type and pixel checks.
 */
public final class ImageDecoder {

    /** Ceiling checked from the header, before the pixels are decoded (~80 MB as ARGB). */
    public static final long MAX_PIXELS = 20_000_000;

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

    /** The decoded pixels, already turned upright when the file carried an EXIF orientation. */
    public record Decoded(Format format, BufferedImage image) {}

    private ImageDecoder() {}

    public static Decoded decode(byte[] bytes, String declaredType, Set<Format> allowed, String unsupportedMessage) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw bad("Imagem inválida");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                Format format = Format.fromReaderName(reader.getFormatName());
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > MAX_PIXELS || pixels <= 0) {
                    throw bad("A imagem deve ter até 20 milhões de pixels");
                }
                if (format == null || !allowed.contains(format)) {
                    throw bad(unsupportedMessage);
                }
                if (!format.contentType().equals(declaredType)) {
                    throw bad("O conteúdo da imagem não corresponde ao tipo informado");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw bad("Imagem inválida");
                }
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

    private static AppException bad(String message) {
        return new AppException(HttpStatus.BAD_REQUEST, message);
    }
}

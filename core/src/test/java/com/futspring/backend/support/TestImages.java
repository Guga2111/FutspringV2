package com.futspring.backend.support;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/** Real image bytes for upload tests: the upload services decode what they receive. */
public final class TestImages {

    private TestImages() {}

    public static byte[] jpeg() {
        return jpeg(4, 2);
    }

    public static byte[] jpeg(int width, int height) {
        return encode(opaque(width, height), "jpeg");
    }

    public static byte[] png() {
        return encode(opaque(4, 2), "png");
    }

    /** 4×2, with half-transparent pixels. */
    public static byte[] pngWithAlpha() {
        BufferedImage image = new BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 2; y++) {
                image.setRGB(x, y, x % 2 == 0 ? 0x80FF0000 : 0xFF0000FF);
            }
        }
        return encode(image, "png");
    }

    /** 8×8 lossy WebP (no alpha), made with {@code cwebp -q 90}. */
    public static byte[] webp() {
        return resource("/images/opaque.webp");
    }

    /** 3000×1500 lossy WebP (no alpha), made with {@code cwebp -q 30} from a generated PNG of 50-px blocks. */
    public static byte[] largeWebp() {
        return resource("/images/large-3000x1500.webp");
    }

    /** 8×8 lossless WebP with half-transparent pixels, made with {@code cwebp -lossless -exact}. */
    public static byte[] webpWithAlpha() {
        return resource("/images/alpha.webp");
    }

    /**
     * A JPEG of {@code width}×{@code height} pixels carrying an EXIF APP1 segment with the given orientation tag
     * and, when {@code gps} is true, a GPS IFD with a latitude reference ("S").
     */
    public static byte[] jpegWithExif(int width, int height, int orientation, boolean gps) {
        byte[] jpeg = jpeg(width, height);
        byte[] tiff = exifTiff(orientation, gps);
        byte[] header = "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1);
        int length = 2 + header.length + tiff.length;
        ByteBuffer app1 = ByteBuffer.allocate(2 + length).order(ByteOrder.BIG_ENDIAN);
        app1.put((byte) 0xFF).put((byte) 0xE1).putShort((short) length).put(header).put(tiff);
        // right after SOI (FFD8)
        ByteBuffer out = ByteBuffer.allocate(jpeg.length + app1.capacity());
        out.put(jpeg, 0, 2).put(app1.array()).put(jpeg, 2, jpeg.length - 2);
        return out.array();
    }

    /** Only the PNG signature and an IHDR of the given size: enough for a reader to report the dimensions. */
    public static byte[] pngHeader(int width, int height) {
        ByteBuffer ihdr = ByteBuffer.allocate(13).putInt(width).putInt(height)
                .put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
        ByteBuffer out = ByteBuffer.allocate(8 + 12 + 13);
        out.put(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        out.putInt(13).put("IHDR".getBytes(StandardCharsets.US_ASCII)).put(ihdr.array());
        CRC32 crc = new CRC32();
        crc.update("IHDR".getBytes(StandardCharsets.US_ASCII));
        crc.update(ihdr.array());
        out.putInt((int) crc.getValue());
        return out.array();
    }

    private static byte[] exifTiff(int orientation, boolean gps) {
        // Little-endian TIFF: IFD0 with Orientation (+ GPS pointer), then the GPS IFD with GPSLatitudeRef
        int entries = gps ? 2 : 1;
        int ifd0Size = 2 + entries * 12 + 4;
        int gpsOffset = 8 + ifd0Size;
        ByteBuffer b = ByteBuffer.allocate(gpsOffset + (gps ? 2 + 12 + 4 : 0)).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
        b.putShort((short) entries);
        b.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        if (gps) {
            b.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(gpsOffset);
        }
        b.putInt(0);
        if (gps) {
            b.putShort((short) 1);
            b.putShort((short) 0x0001).putShort((short) 2).putInt(2).put((byte) 'S').put((byte) 0).putShort((short) 0);
            b.putInt(0);
        }
        return b.array();
    }

    private static BufferedImage opaque(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(new Color(30, 120, 200));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] resource(String path) {
        try (InputStream in = TestImages.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource " + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

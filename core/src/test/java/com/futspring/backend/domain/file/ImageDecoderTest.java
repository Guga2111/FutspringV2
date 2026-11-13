package com.futspring.backend.domain.file;

import com.futspring.backend.domain.file.ImageDecoder.Format;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.support.TestImages;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageDecoderTest {

    private static final Set<Format> ALL = EnumSet.allOf(Format.class);

    @Test
    void textDeclaredAsPng_isRejected() {
        byte[] text = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> ImageDecoder.decode(text, "image/png", ALL, "x", 2048))
                .isInstanceOf(AppException.class).hasMessage("Imagem inválida");
    }

    @Test
    void declaredTypeDifferentFromContent_isRejected() {
        assertThatThrownBy(() -> ImageDecoder.decode(TestImages.png(), "image/jpeg", ALL, "x", 2048))
                .isInstanceOf(AppException.class)
                .hasMessage("O conteúdo da imagem não corresponde ao tipo informado");
    }

    @Test
    void tooManyPixels_isRejectedFromTheHeader() {
        assertThatThrownBy(() -> ImageDecoder.decode(TestImages.pngHeader(10_000, 10_000), "image/png", ALL, "x", 2048))
                .isInstanceOf(AppException.class)
                .hasMessage("A imagem deve ter até 20 milhões de pixels");
    }

    @Test
    void formatOutsideTheAllowedSet_usesTheCallersMessage() {
        assertThatThrownBy(() -> ImageDecoder.decode(TestImages.webp(), "image/webp",
                EnumSet.of(Format.JPEG, Format.PNG), "Somente JPEG e PNG são permitidos", 2048))
                .isInstanceOf(AppException.class)
                .hasMessage("Somente JPEG e PNG são permitidos");
    }

    @Test
    void decodesEachFormat() {
        assertThat(ImageDecoder.decode(TestImages.jpeg(), "image/jpeg", ALL, "x", 2048).format()).isEqualTo(Format.JPEG);
        assertThat(ImageDecoder.decode(TestImages.png(), "image/png", ALL, "x", 2048).format()).isEqualTo(Format.PNG);
        var webp = ImageDecoder.decode(TestImages.webp(), "image/webp", ALL, "x", 2048);
        assertThat(webp.format()).isEqualTo(Format.WEBP);
        assertThat(webp.image().getWidth()).isEqualTo(8);
    }

    @Test
    void exifOrientation_isApplied() {
        // orientation 6: stored landscape, shown rotated 90° clockwise
        var decoded = ImageDecoder.decode(TestImages.jpegWithExif(40, 20, 6, false), "image/jpeg", ALL, "x", 2048);
        assertThat(decoded.image().getWidth()).isEqualTo(20);
        assertThat(decoded.image().getHeight()).isEqualTo(40);
    }

    @Test
    void normalOrientation_keepsTheSize() {
        var decoded = ImageDecoder.decode(TestImages.jpegWithExif(40, 20, 1, false), "image/jpeg", ALL, "x", 2048);
        assertThat(decoded.image().getWidth()).isEqualTo(40);
    }

    // --- memory: decoded at reduced size ---

    @Test
    void readersSkipPixelsWhileReading() throws Exception {
        // the memory bound relies on each reader honouring source subsampling instead of decoding everything first
        for (byte[] bytes : new byte[][]{TestImages.jpeg(3000, 1500), png(3000, 1500), TestImages.largeWebp()}) {
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                ImageReader reader = ImageIO.getImageReaders(input).next();
                reader.setInput(input);
                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(3, 3, 0, 0);
                BufferedImage image = reader.read(0, param);
                assertThat(image.getWidth()).as(reader.getFormatName()).isEqualTo(1000);
                assertThat(image.getHeight()).as(reader.getFormatName()).isEqualTo(500);
                reader.dispose();
            }
        }
    }

    @Test
    void largeImage_isScaledToTheMaxSide() {
        var jpeg = ImageDecoder.decode(TestImages.jpeg(3000, 1500), "image/jpeg", ALL, "x", 2048).image();
        assertThat(jpeg.getWidth()).isEqualTo(2048);
        assertThat(jpeg.getHeight()).isEqualTo(1024);
        assertThat(jpeg.getType()).isEqualTo(BufferedImage.TYPE_INT_RGB);

        var webp = ImageDecoder.decode(TestImages.largeWebp(), "image/webp", ALL, "x", 2048).image();
        assertThat(webp.getWidth()).isEqualTo(2048);
        assertThat(webp.getHeight()).isEqualTo(1024);

        var small = ImageDecoder.decode(TestImages.largeWebp(), "image/webp", ALL, "x", 256).image();
        assertThat(small.getWidth()).isEqualTo(256);
        assertThat(small.getHeight()).isEqualTo(128);
    }

    @Test
    void smallImage_keepsItsSize() {
        var image = ImageDecoder.decode(TestImages.jpeg(40, 20), "image/jpeg", ALL, "x", 2048).image();
        assertThat(image.getWidth()).isEqualTo(40);
        assertThat(image.getHeight()).isEqualTo(20);
    }

    @Test
    void scaledImage_isStillTurnedUpright() {
        var image = ImageDecoder.decode(TestImages.jpegWithExif(4000, 2000, 6, false), "image/jpeg", ALL, "x", 2048)
                .image();
        assertThat(image.getWidth()).isEqualTo(1024);
        assertThat(image.getHeight()).isEqualTo(2048);
    }

    @Test
    void transparency_survivesTheScaling() {
        BufferedImage source = new BufferedImage(3000, 1500, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 3000, 1500, new int[3000 * 1500], 0, 3000); // all transparent
        var image = ImageDecoder.decode(encode(source), "image/png", ALL, "x", 2048).image();
        assertThat(image.getColorModel().hasAlpha()).isTrue();
        assertThat(image.getRGB(10, 10) >>> 24).isZero();
    }

    // --- concurrency ---

    @Test
    void permits_refuseAThirdImageAtOnce_andComeBack() {
        AppException refused = ImageDecoder.withPermit(() -> ImageDecoder.withPermit(() -> {
            try {
                ImageDecoder.withPermit(() -> "third");
                return null;
            } catch (AppException e) {
                return e;
            }
        }));
        assertThat(refused).isNotNull();
        assertThat(refused.getStatus().value()).isEqualTo(503);
        assertThat(refused).hasMessage("Muitas imagens sendo enviadas agora. Tente de novo em instantes.");
        // released after use, also when the work throws
        assertThatThrownBy(() -> ImageDecoder.withPermit(() -> { throw new IllegalStateException("boom"); }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(ImageDecoder.withPermit(() -> ImageDecoder.withPermit(() -> "ok"))).isEqualTo("ok");
    }

    private static byte[] png(int width, int height) {
        return encode(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB));
    }

    private static byte[] encode(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}

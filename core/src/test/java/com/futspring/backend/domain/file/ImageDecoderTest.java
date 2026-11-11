package com.futspring.backend.domain.file;

import com.futspring.backend.domain.file.ImageDecoder.Format;
import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.support.TestImages;
import org.junit.jupiter.api.Test;

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
        assertThatThrownBy(() -> ImageDecoder.decode(text, "image/png", ALL, "x"))
                .isInstanceOf(AppException.class).hasMessage("Imagem inválida");
    }

    @Test
    void declaredTypeDifferentFromContent_isRejected() {
        assertThatThrownBy(() -> ImageDecoder.decode(TestImages.png(), "image/jpeg", ALL, "x"))
                .isInstanceOf(AppException.class)
                .hasMessage("O conteúdo da imagem não corresponde ao tipo informado");
    }

    @Test
    void tooManyPixels_isRejectedFromTheHeader() {
        assertThatThrownBy(() -> ImageDecoder.decode(TestImages.pngHeader(10_000, 10_000), "image/png", ALL, "x"))
                .isInstanceOf(AppException.class)
                .hasMessage("A imagem deve ter até 20 milhões de pixels");
    }

    @Test
    void formatOutsideTheAllowedSet_usesTheCallersMessage() {
        assertThatThrownBy(() -> ImageDecoder.decode(TestImages.webp(), "image/webp",
                EnumSet.of(Format.JPEG, Format.PNG), "Somente JPEG e PNG são permitidos"))
                .isInstanceOf(AppException.class)
                .hasMessage("Somente JPEG e PNG são permitidos");
    }

    @Test
    void decodesEachFormat() {
        assertThat(ImageDecoder.decode(TestImages.jpeg(), "image/jpeg", ALL, "x").format()).isEqualTo(Format.JPEG);
        assertThat(ImageDecoder.decode(TestImages.png(), "image/png", ALL, "x").format()).isEqualTo(Format.PNG);
        var webp = ImageDecoder.decode(TestImages.webp(), "image/webp", ALL, "x");
        assertThat(webp.format()).isEqualTo(Format.WEBP);
        assertThat(webp.image().getWidth()).isEqualTo(8);
    }

    @Test
    void exifOrientation_isApplied() {
        // orientation 6: stored landscape, shown rotated 90° clockwise
        var decoded = ImageDecoder.decode(TestImages.jpegWithExif(40, 20, 6, false), "image/jpeg", ALL, "x");
        assertThat(decoded.image().getWidth()).isEqualTo(20);
        assertThat(decoded.image().getHeight()).isEqualTo(40);
    }

    @Test
    void normalOrientation_keepsTheSize() {
        var decoded = ImageDecoder.decode(TestImages.jpegWithExif(40, 20, 1, false), "image/jpeg", ALL, "x");
        assertThat(decoded.image().getWidth()).isEqualTo(40);
    }
}

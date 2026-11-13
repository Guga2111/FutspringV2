package com.futspring.backend.domain.file;

import com.futspring.backend.shared.exception.AppException;
import com.futspring.backend.support.TestImages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileUploadServiceTest {

    @TempDir
    Path dir;

    FileUploadService service;

    @BeforeEach
    void setUp() {
        service = new FileUploadService();
        ReflectionTestUtils.setField(service, "uploadsDir", dir.toString());
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private String upload(byte[] bytes, String type) {
        return service.uploadImage(new MockMultipartFile("file", "photo", type, bytes));
    }

    private BufferedImage stored(String filename) throws IOException {
        return ImageIO.read(dir.resolve(filename).toFile());
    }

    @Test
    void textDeclaredAsPng_isRejectedAndNothingIsStored() throws IOException {
        byte[] text = "not an image".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> upload(text, "image/png")).isInstanceOf(AppException.class);
        try (var files = Files.list(dir)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void jpegWithGps_isStoredWithoutExif() throws IOException {
        byte[] original = TestImages.jpegWithExif(40, 20, 1, true);
        assertThat(new String(original, StandardCharsets.ISO_8859_1)).contains("Exif");

        String filename = upload(original, "image/jpeg");

        assertThat(filename).endsWith(".jpg");
        byte[] saved = Files.readAllBytes(dir.resolve(filename));
        assertThat(new String(saved, StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
        assertThat(hasApp1(saved)).isFalse();
        assertThat(stored(filename).getWidth()).isEqualTo(40);
    }

    @Test
    void largePhoto_isStoredAtMost2048PxOnTheLongSide() throws IOException {
        String filename = upload(TestImages.jpeg(3000, 1500), "image/jpeg");
        BufferedImage image = stored(filename);
        assertThat(image.getWidth()).isEqualTo(FileUploadService.MAX_SIDE);
        assertThat(image.getHeight()).isEqualTo(1024);
    }

    @Test
    void rotatedJpeg_isStoredUpright() throws IOException {
        String filename = upload(TestImages.jpegWithExif(40, 20, 6, false), "image/jpeg");
        BufferedImage image = stored(filename);
        assertThat(image.getWidth()).isEqualTo(20);
        assertThat(image.getHeight()).isEqualTo(40);
    }

    @Test
    void pngWithAlpha_staysPng() throws IOException {
        String filename = upload(TestImages.pngWithAlpha(), "image/png");
        assertThat(filename).endsWith(".png");
        BufferedImage image = stored(filename);
        assertThat(image.getColorModel().hasAlpha()).isTrue();
        assertThat(image.getRGB(0, 0) >>> 24).isEqualTo(0x80);
    }

    @Test
    void opaquePng_becomesJpeg() {
        assertThat(upload(TestImages.png(), "image/png")).endsWith(".jpg");
    }

    @Test
    void webp_isAcceptedAndStoredAsJpegOrPng() throws IOException {
        String opaque = upload(TestImages.webp(), "image/webp");
        assertThat(opaque).endsWith(".jpg");
        assertThat(stored(opaque).getWidth()).isEqualTo(8);

        String alpha = upload(TestImages.webpWithAlpha(), "image/webp");
        assertThat(alpha).endsWith(".png");
        assertThat(stored(alpha).getColorModel().hasAlpha()).isTrue();
    }

    @Test
    void newUpload_isDeletedOnRollbackAndKeptOnCommit() {
        TransactionSynchronizationManager.initSynchronization();
        String rolledBack = upload(TestImages.jpeg(), "image/jpeg");
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(dir.resolve(rolledBack)).doesNotExist();

        TransactionSynchronizationManager.initSynchronization();
        String committed = upload(TestImages.jpeg(), "image/jpeg");
        complete(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(dir.resolve(committed)).exists();
    }

    @Test
    void deleteImageAfterCommit_onlyDeletesWhenTheTransactionCommits() throws IOException {
        Files.write(dir.resolve("kept.jpg"), TestImages.jpeg());
        Files.write(dir.resolve("gone.jpg"), TestImages.jpeg());

        TransactionSynchronizationManager.initSynchronization();
        service.deleteImageAfterCommit("kept.jpg");
        complete(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(dir.resolve("kept.jpg")).exists();

        TransactionSynchronizationManager.initSynchronization();
        service.deleteImageAfterCommit("gone.jpg");
        assertThat(dir.resolve("gone.jpg")).exists();
        complete(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(dir.resolve("gone.jpg")).doesNotExist();
    }

    private static void complete(int status) {
        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        TransactionSynchronizationManager.clearSynchronization();
        synchronizations.forEach(s -> s.afterCompletion(status));
    }

    /** Walks the JPEG markers up to the scan, looking for an APP1 (EXIF/XMP) segment. */
    private static boolean hasApp1(byte[] jpeg) {
        int i = 2;
        while (i + 4 < jpeg.length && (jpeg[i] & 0xFF) == 0xFF) {
            int marker = jpeg[i + 1] & 0xFF;
            if (marker == 0xE1) {
                return true;
            }
            if (marker == 0xDA) {
                return false;
            }
            i += 2 + (((jpeg[i + 2] & 0xFF) << 8) | (jpeg[i + 3] & 0xFF));
        }
        return false;
    }
}

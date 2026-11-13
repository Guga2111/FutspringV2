package com.futspring.backend.domain.file;

import com.futspring.backend.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {

    static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    /** Longest side of a stored public image (avatars, pelada and champion photos are shown well below it). */
    static final int MAX_SIDE = 2048;
    private static final float JPEG_QUALITY = 0.9f;

    @Value("${app.uploads.dir:uploads}")
    private String uploadsDir;

    public String uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Nenhum arquivo enviado");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new AppException(HttpStatus.BAD_REQUEST, "A imagem deve ter no máximo 5 MB");
        }

        Encoded encoded = ImageDecoder.withPermit(() -> encode(file));
        byte[] content = encoded.content();
        String filename = UUID.randomUUID() + encoded.extension();

        try {
            Path uploadPath = Paths.get(uploadsDir);
            Files.createDirectories(uploadPath);
            Files.write(uploadPath.resolve(filename), content, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            log.error("Failed to store upload {}", filename, e);
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível salvar o arquivo");
        }

        // A new file whose transaction rolls back would be orphaned: remove it in that case
        runOnCompletion(false, filename);
        return filename;
    }

    private record Encoded(byte[] content, String extension) {}

    // Re-encoded from the pixels: EXIF (GPS included), ICC and anything appended to the file are dropped
    private static Encoded encode(MultipartFile file) {
        try {
            ImageDecoder.Decoded decoded = ImageDecoder.decode(file.getBytes(), file.getContentType(),
                    EnumSet.allOf(ImageDecoder.Format.class), "Somente imagens JPEG, PNG ou WebP são permitidas",
                    MAX_SIDE);
            boolean alpha = decoded.image().getColorModel().hasAlpha();
            return alpha ? new Encoded(encodePng(decoded.image()), ".png")
                    : new Encoded(encodeJpeg(decoded.image()), ".jpg");
        } catch (IOException e) {
            log.error("Failed to re-encode an upload", e);
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível salvar o arquivo");
        }
    }

    private static byte[] encodePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) {
            throw new IOException("No PNG writer");
        }
        return out.toByteArray();
    }

    private static byte[] encodeJpeg(BufferedImage source) throws IOException {
        // The JPEG writer takes plain RGB: grayscale, indexed and CMYK sources are drawn onto one first (a scaled image
        // already is one, so no second copy)
        BufferedImage rgb = source;
        if (source.getType() != BufferedImage.TYPE_INT_RGB) {
            rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = rgb.createGraphics();
            try {
                graphics.drawImage(source, 0, 0, null);
            } finally {
                graphics.dispose();
            }
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    public void deleteImage(String filename) {
        if (filename == null || filename.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(uploadsDir).resolve(filename));
        } catch (IOException e) {
            log.warn("Could not delete upload {}", filename, e);
        }
    }

    /** Deletes the file once the current transaction commits (immediately when there is no transaction). */
    public void deleteImageAfterCommit(String filename) {
        if (filename == null || filename.isBlank()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteImage(filename);
            return;
        }
        runOnCompletion(true, filename);
    }

    private void runOnCompletion(boolean onCommit, String filename) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                boolean committed = status == STATUS_COMMITTED;
                if (committed == onCommit) {
                    deleteImage(filename);
                }
            }
        });
    }
}

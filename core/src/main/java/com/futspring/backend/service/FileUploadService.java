package com.futspring.backend.service;

import com.futspring.backend.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadService {

    static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;

    // The stored extension comes from the validated content type, never from the client filename
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    @Value("${app.uploads.dir:uploads}")
    private String uploadsDir;

    public String uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Nenhum arquivo enviado");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new AppException(HttpStatus.BAD_REQUEST, "A imagem deve ter no máximo 5 MB");
        }

        String extension = EXTENSIONS.get(file.getContentType());
        if (extension == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Somente imagens JPEG, PNG ou WebP são permitidas");
        }
        String filename = UUID.randomUUID() + extension;

        try {
            Path uploadPath = Paths.get(uploadsDir);
            Files.createDirectories(uploadPath);
            Files.copy(file.getInputStream(), uploadPath.resolve(filename));
        } catch (IOException e) {
            log.error("Failed to store upload {}", filename, e);
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível salvar o arquivo");
        }

        // A new file whose transaction rolls back would be orphaned: remove it in that case
        runOnCompletion(false, filename);
        return filename;
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

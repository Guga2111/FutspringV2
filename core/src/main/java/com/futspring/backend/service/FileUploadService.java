package com.futspring.backend.service;

import com.futspring.backend.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileUploadService {

    @Value("${app.uploads.dir:uploads}")
    private String uploadsDir;

    public String uploadImage(MultipartFile file) {
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new AppException(HttpStatus.BAD_REQUEST, "File size exceeds 5MB limit");
        }

        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png") && !contentType.equals("image/webp"))) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Only JPEG, PNG, and WebP images are allowed");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".jpg";
        String filename = UUID.randomUUID().toString() + extension;

        try {
            Path uploadPath = Paths.get(uploadsDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            Files.copy(file.getInputStream(), uploadPath.resolve(filename));
        } catch (IOException e) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
        }

        return filename;
    }

    public void deleteImage(String filename) {
        if (filename != null && !filename.isBlank()) {
            try {
                Files.deleteIfExists(Paths.get(uploadsDir).resolve(filename));
            } catch (IOException ignored) {
                // best-effort deletion
            }
        }
    }
}

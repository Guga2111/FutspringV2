package com.futspring.backend.domain.finance;

import com.futspring.backend.shared.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Private storage. No file from these directories is served by the public FileController. */
@Component
@Slf4j
public class FinanceFiles {
    private final Path receipts;
    private final Path qr;
    public FinanceFiles(@Value("${app.finance.receipts-dir:finance-receipts}") String receipts,
                        @Value("${app.finance.qr-dir:finance-qr}") String qr,
                        @Value("${app.uploads.dir:uploads}") String publicUploads) {
        this.receipts = Path.of(receipts).toAbsolutePath().normalize();
        this.qr = Path.of(qr).toAbsolutePath().normalize();
        Path publicPath = Path.of(publicUploads).toAbsolutePath().normalize();
        if (this.receipts.startsWith(publicPath) || this.qr.startsWith(publicPath) || this.qr.startsWith(this.receipts)
                || this.receipts.startsWith(this.qr)) throw new IllegalStateException("Financial files need separate private directories");
    }
    record Verified(byte[] bytes, String contentType, String extension, String hash) {}
    Verified verify(MultipartFile file) {
        if (file == null || file.isEmpty()) throw bad("Selecione uma imagem JPEG ou PNG");
        if (file.getSize() > 5 * 1024 * 1024) throw bad("A imagem deve ter no máximo 5 MB");
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length > 5 * 1024 * 1024) throw bad("A imagem deve ter no máximo 5 MB");
            String format;
            try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw bad("Imagem inválida");
                var reader = readers.next();
                try {
                    reader.setInput(input); format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                    if (pixels > 20_000_000 || pixels <= 0) throw bad("A imagem deve ter até 20 milhões de pixels");
                    if (!Set.of("jpeg", "png").contains(format)) throw bad("Somente JPEG e PNG são permitidos");
                    if (reader.read(0) == null) throw bad("Imagem inválida");
                } finally { reader.dispose(); }
            }
            String type = format.equals("jpeg") ? "image/jpeg" : "image/png";
            if (!type.equals(file.getContentType())) throw bad("O conteúdo da imagem não corresponde ao tipo informado");
            return new Verified(bytes, type, format.equals("jpeg") ? ".jpg" : ".png",
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (IOException e) { throw bad("Imagem inválida"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    String store(Verified image, boolean isQr) {
        Path dir = directory(isQr); String key = UUID.randomUUID() + image.extension();
        // Register before I/O so any subsequent failure rolls back both row and file.
        if (!TransactionSynchronizationManager.isSynchronizationActive()) throw new IllegalStateException("Financial upload requires a transaction");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try { delete(key, isQr); } catch (IOException e) { log.warn("Financial rollback file cleanup failed: {}", key, e); }
                }
            }
        });
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(key), image.bytes(), StandardOpenOption.CREATE_NEW);
            return key;
        } catch (IOException e) {
            try { delete(key, isQr); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            log.error("Financial file storage failed", e);
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível armazenar a imagem");
        }
    }
    byte[] read(String key, boolean isQr) {
        try { return Files.readAllBytes(path(key, isQr)); }
        catch (IOException e) { throw new AppException(HttpStatus.NOT_FOUND, "Arquivo indisponível"); }
    }
    void delete(String key, boolean isQr) throws IOException { Files.deleteIfExists(path(key, isQr)); }
    void deleteAfterCommit(String key, boolean isQr) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try { delete(key, isQr); } catch (IOException e) { log.warn("Financial file cleanup failed: {}", key, e); }
            }
        });
    }
    Path directory(boolean isQr) { return isQr ? qr : receipts; }
    private Path path(String key, boolean isQr) {
        if (key == null || !key.matches("[a-f0-9-]{36}\\.(jpg|png)")) throw bad("Arquivo inválido");
        return directory(isQr).resolve(key);
    }
    private static AppException bad(String message) { return new AppException(HttpStatus.BAD_REQUEST, message); }
}

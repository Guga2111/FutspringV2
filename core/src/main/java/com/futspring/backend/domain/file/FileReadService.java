package com.futspring.backend.domain.file;

import com.futspring.backend.domain.user.UserRepository;
import com.futspring.backend.domain.pelada.PeladaRepository;
import com.futspring.backend.domain.daily.DailyRepository;
import com.futspring.backend.shared.helper.UserAuthenticationHelper;
import com.futspring.backend.shared.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.MalformedURLException;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class FileReadService {
    private final UserAuthenticationHelper authentication;
    private final UserRepository users;
    private final PeladaRepository peladas;
    private final DailyRepository dailies;
    @Value("${app.uploads.dir:uploads}")
    private String uploadsDir;

    @Transactional(readOnly = true)
    public Resource read(String filename, String email) {
        if (filename.contains("\0") || filename.contains("/") || filename.contains("\\") || filename.equals("..")) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Nome de arquivo inválido");
        }
        var caller = authentication.getAuthenticatedUser(email);
        boolean allowed = users.findByImageOrBackgroundImage(filename, filename).stream()
                .filter(owner -> !owner.isDeleted())
                .anyMatch(owner -> owner.getId().equals(caller.getId())
                        || peladas.existsSharedPelada(owner.getId(), caller.getId()));
        allowed = allowed || peladas.existsByImageAndMembers_Id(filename, caller.getId())
                || dailies.existsByChampionImageAndPelada_Members_Id(filename, caller.getId());
        if (!allowed) throw missing();
        Path directory = Path.of(uploadsDir).toAbsolutePath().normalize();
        Path path = directory.resolve(filename).normalize();
        if (!path.startsWith(directory)) throw missing();
        try {
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable() || !java.nio.file.Files.isRegularFile(path)) throw missing();
            return resource;
        } catch (MalformedURLException e) { throw missing(); }
    }
    private static AppException missing() {
        return new AppException(HttpStatus.NOT_FOUND, "Imagem não encontrada");
    }
}

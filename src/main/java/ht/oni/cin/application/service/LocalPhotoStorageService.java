package ht.oni.cin.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

@Service
@Profile("local")
@RequiredArgsConstructor
@Slf4j
public class LocalPhotoStorageService implements PhotoStorage {

    private final EncryptionService encryptionService;
    private Path storageDir;

    @PostConstruct
    void init() throws IOException {
        storageDir = Path.of("data", "photos");
        Files.createDirectories(storageDir);
        log.info("Mode local : photos dans {}", storageDir.toAbsolutePath());
    }

    @Override
    public String storePhoto(String nin, String photoBase64) {
        try {
            byte[] raw = Base64.getDecoder().decode(photoBase64);
            String encrypted = encryptionService.encrypt(Base64.getEncoder().encodeToString(raw));
            String fileName = nin + "_" + UUID.randomUUID() + ".enc";
            Path target = storageDir.resolve(fileName);
            Files.writeString(target, encrypted);
            return "local://" + target.toAbsolutePath();
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de stocker la photo localement", e);
        }
    }
}

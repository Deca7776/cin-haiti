package ht.oni.cin.application.service;

import ht.oni.cin.config.CinProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.UUID;

@Service
@Profile("!local")
@RequiredArgsConstructor
@Slf4j
public class MinioPhotoStorageService implements PhotoStorage {

    private final CinProperties properties;
    private final EncryptionService encryptionService;
    private MinioClient minioClient;

    @PostConstruct
    void init() {
        minioClient = MinioClient.builder()
                .endpoint(properties.getMinio().getEndpoint())
                .credentials(properties.getMinio().getAccessKey(), properties.getMinio().getSecretKey())
                .build();
        ensureBucket();
    }

    @Override
    public String storePhoto(String nin, String photoBase64) {
        try {
            byte[] raw = Base64.getDecoder().decode(photoBase64);
            String encrypted = encryptionService.encrypt(Base64.getEncoder().encodeToString(raw));
            byte[] payload = encrypted.getBytes();
            String objectName = nin + "/" + UUID.randomUUID() + ".enc";

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getMinio().getBucket())
                    .object(objectName)
                    .stream(new ByteArrayInputStream(payload), payload.length, -1)
                    .contentType("application/octet-stream")
                    .build());

            return properties.getMinio().getBucket() + "/" + objectName;
        } catch (Exception e) {
            log.error("Stockage photo MinIO échoué", e);
            throw new IllegalStateException("Impossible de stocker la photo", e);
        }
    }

    private void ensureBucket() {
        try {
            String bucket = properties.getMinio().getBucket();
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Bucket MinIO créé: {}", bucket);
            }
        } catch (Exception e) {
            log.warn("MinIO indisponible au démarrage — mode dégradé: {}", e.getMessage());
        }
    }
}

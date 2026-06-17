package ht.oni.cin.config;

import ht.oni.cin.infrastructure.persistence.entity.CinApiClientEntity;
import ht.oni.cin.infrastructure.persistence.repository.CinApiClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Configuration
@Profile("local")
@RequiredArgsConstructor
@Slf4j
public class LocalDataInitializer {

    private final CinApiClientRepository apiClientRepository;

    @Bean
    CommandLineRunner seedDemoApiClient() {
        return args -> {
            if (apiClientRepository.findByClientName("crm-demo").isEmpty()) {
                CinApiClientEntity client = CinApiClientEntity.builder()
                        .clientName("crm-demo")
                        .apiKeyHash("bc639ba4b0be8526fe018fbeb010106ebf0e60c0fd6044a9f2d299f408412a3c")
                        .allowedFields(new String[]{"nin", "nom", "prenom", "date_naissance", "sexe", "departement"})
                        .canAccessPhoto(false)
                        .active(true)
                        .createdAt(Instant.now())
                        .expiresAt(Instant.now().plus(90, ChronoUnit.DAYS))
                        .lastRotatedAt(Instant.now())
                        .build();
                apiClientRepository.save(client);
                log.info("Client API démo 'crm-demo' initialisé (clé: demo-api-key-2024)");
            }
        };
    }
}

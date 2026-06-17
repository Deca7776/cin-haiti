package ht.oni.cin.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI cinOpenAPI() {
        final String bearerScheme = "bearerAuth";
        final String apiKeyScheme = "apiKeyAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("CIN Haiti — Microservice de Numérisation")
                        .description("""
                                ## Guide rapide Swagger
                                1. `GET /api/v1/dev/token` → copier le **token**
                                2. Cliquer **Authorize** → coller `Bearer <token>` (ou juste le token selon le champ)
                                3. `POST /api/v1/scan/sessions` → noter le **sessionId**
                                4. `POST /api/v1/scan/{sessionId}/image` → uploader une image PNG/JPEG
                                5. `POST /api/v1/scan/{sessionId}/validate` → valider avec le **idempotencyToken** de l'étape 4
                                6. Export : Authorize avec JWT + header `X-API-Key: demo-api-key-2024`
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("ONI / Équipe CIN").email("contact@oni.gouv.ht"))
                        .license(new License().name("Usage institutionnel")))
                .components(new Components()
                        .addSecuritySchemes(bearerScheme, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Jeton JWT opératrice — obtenir via GET /api/v1/dev/token"))
                        .addSecuritySchemes(apiKeyScheme, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key")
                                .description("Clé API tiers — démo : demo-api-key-2024")))
                .addSecurityItem(new SecurityRequirement().addList(bearerScheme));
    }
}

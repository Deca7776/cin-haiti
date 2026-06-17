package ht.oni.cin;

import ht.oni.cin.security.ApiClientPrincipal;
import ht.oni.cin.security.OperatorPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ht.oni.cin.infrastructure.persistence.entity.CinApiClientEntity;
import ht.oni.cin.infrastructure.persistence.repository.CinApiClientRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CinIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CinApiClientRepository apiClientRepository;

    @Test
    void fullScanValidateExportFlow() throws Exception {
        String operatorId = "operatrice-demo";
        String operatorToken = "test-token";

        // 1. Ouvrir session
        MvcResult sessionResult = mockMvc.perform(post("/api/v1/scan/sessions")
                        .with(authentication(new OperatorPrincipal(operatorId, operatorToken))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode session = objectMapper.readTree(sessionResult.getResponse().getContentAsString());
        UUID sessionId = UUID.fromString(session.get("sessionId").asText());

        // 3. Upload image test
        byte[] imageBytes = createTestCardImage();
        MockMultipartFile file = new MockMultipartFile("file", "cin-test.png", "image/png", imageBytes);

        MvcResult ocrResult = mockMvc.perform(multipart("/api/v1/scan/" + sessionId + "/image")
                        .file(file)
                        .with(authentication(new OperatorPrincipal(operatorId, operatorToken))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode ocr = objectMapper.readTree(ocrResult.getResponse().getContentAsString());
        assertTrue(ocr.get("ocrSuccess").asBoolean(), "OCR doit réussir en mode local");
        String idempotencyToken = ocr.get("idempotencyToken").asText();

        // 4. Valider et enregistrer
        String nin = String.format("%013d", Math.abs(UUID.randomUUID().getMostSignificantBits()) % 1_000_000_000_0000L);
        String validateBody = """
                {
                  "numeroCarte": "T1K2N89G7",
                  "nin": "%s",
                  "ninDisplay": "0123456789",
                  "nom": "DUPONT",
                  "prenom": "Jean",
                  "nationalite": "HTI",
                  "dateNaissance": "15/03/1990",
                  "lieuNaissance": "Port-au-Prince, Ouest",
                  "sexe": "M",
                  "adresse": "Delmas 33",
                  "departement": "Ouest",
                  "dateEmission": "01/06/2020",
                  "dateExpiration": "01/06/2030",
                  "idempotencyToken": "%s",
                  "consentementDocumente": true,
                  "baseLegale": "consentement_titulaire",
                  "confirmDuplicateCheck": true,
                  "scoreOcrMoyen": 88.5
                }
                """.formatted(nin, idempotencyToken);

        mockMvc.perform(post("/api/v1/scan/" + sessionId + "/validate")
                        .with(authentication(new OperatorPrincipal(operatorId, operatorToken)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nin").value(nin));

        // 5. Export vers système tiers
        CinApiClientEntity client = apiClientRepository.findByClientName("crm-demo").orElseThrow();

        mockMvc.perform(get("/api/v1/identites/" + nin)
                        .with(authentication(new ApiClientPrincipal(client, "demo-api-key-2024"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nin").value(nin))
                .andExpect(jsonPath("$.data.nom").value("DUPONT"));
    }

    private byte[] createTestCardImage() throws Exception {
        // Layout CIN haïtienne : photo à gauche, texte à droite (zone OCR)
        int w = 1200;
        int h = 750;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(200, 200, 200));
        g.fillRect(40, 160, 280, 420);
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 22));
        int tx = 380;
        g.drawString("REPUBLIQUE D'HAITI", tx, 80);
        g.drawString("CARTE D'IDENTIFICATION NATIONALE", tx, 110);
        g.setFont(new Font("Arial", Font.PLAIN, 18));
        g.drawString("Numéro de carte / Nimewo kat la", tx, 130);
        g.drawString("T1K2N89G7", tx, 155);
        g.drawString("Prénom / Non", tx, 190);
        g.drawString("JEAN", tx, 215);
        g.drawString("Nom / Siyati", tx, 250);
        g.drawString("DUPONT", tx, 275);
        g.drawString("Sexe / Sèks", tx, 310);
        g.drawString("M", tx, 335);
        g.drawString("Nationalité / Nasyonalite", tx, 360);
        g.drawString("Haïtien", tx, 385);
        g.drawString("Date de Naissance / Dat ou fèt", tx, 410);
        g.drawString("15-03-1990", tx, 435);
        g.drawString("Lieu de Naissance / Kote ou fèt", tx, 460);
        g.drawString("Département Ouest, Commune Port-au-Prince", tx, 485);
        g.drawString("Date d'émission / Dat kat la fèt", tx, 510);
        g.drawString("01-06-2020", tx, 535);
        g.drawString("Date d'expiration / Dat kat la fini", tx, 560);
        g.drawString("01-06-2030", tx, 585);
        g.drawString("Numéro d'identification unique", tx, 610);
        g.drawString("1234567890123", tx, 635);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }
}

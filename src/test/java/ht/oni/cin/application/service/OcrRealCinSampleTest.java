package ht.oni.cin.application.service;

import ht.oni.cin.domain.model.OcrExtractionResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class OcrRealCinSampleTest {

    @Autowired
    private OcrService ocrService;

    @Autowired
    private ImageProcessingService imageProcessingService;

    @Test
    void extract_patriciaDelaireSampleImage() throws Exception {
        Path sample = Path.of("data/samples/cin-patricia-delaire.png");
        assumeSampleExists(sample);

        byte[] raw = Files.readAllBytes(sample);
        OcrExtractionResult result = ocrService.extract(raw);

        System.out.println("=== OCR BRUT ===\n" + result.getRawText());
        System.out.println("=== CHAMPS ===");
        result.getFields().forEach((k, v) -> System.out.println(k + " = " + v.getValue() + " (" + v.getConfidence() + "%)"));

        assertNotNull(result.getRawText());
        assertFalse(result.getRawText().isBlank(), "Tesseract doit produire du texte");

        var fields = result.getFields();
        assertTrue(fields.containsKey("prenom") || fields.containsKey("numero_carte"),
                "Au moins prénom ou numéro carte — OCR: " + snippet(result));
        if (fields.containsKey("nin_display")) {
            assertEquals("0123456789", fields.get("nin_display").getValue());
        }
    }

    private static void assumeSampleExists(Path sample) {
        if (!Files.exists(sample)) {
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Image échantillon absente: " + sample);
        }
    }

    private static String snippet(OcrExtractionResult result) {
        String raw = result.getRawText().replace('\n', ' ');
        return raw.substring(0, Math.min(300, raw.length()));
    }
}

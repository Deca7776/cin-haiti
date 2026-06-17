package ht.oni.cin.application.service;

import ht.oni.cin.config.CinProperties;
import ht.oni.cin.domain.model.OcrExtractionResult;
import ht.oni.cin.domain.model.OcrFieldResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OcrService {

    private static final int[] PSM_MODES = {6, 4, 3};
    private static final int EXPECTED_CARD_FIELDS = 11;

    private final CinProperties properties;
    private final HaitianCinParser haitianCinParser;
    private final CinRoiOcrService cinRoiOcrService;
    private final ImageProcessingService imageProcessingService;
    private final TesseractRunner tesseractRunner;

    /** OCR hybride : zones ROI (prioritaire) + texte intégral (secours). */
    public OcrExtractionResult extract(byte[] rawImageBytes) {
        try {
            double threshold = properties.getOcr().getConfidenceThreshold();

            Map<String, OcrFieldResult> roiFields = cinRoiOcrService.extract(rawImageBytes, threshold);
            Map<String, OcrFieldResult> textFields = extractFromFullText(rawImageBytes, threshold);

            Map<String, OcrFieldResult> fields = haitianCinParser.merge(roiFields, textFields);
            int extracted = countCardFields(fields);
            double avg = fields.values().stream().mapToDouble(OcrFieldResult::getConfidence).average().orElse(0);
            boolean hasRequired = hasRequiredFields(fields);

            String method = roiFields.size() >= textFields.size() ? "ROI+texte" : "texte+ROI";
            log.info("OCR {} — {}/{} champs, confiance moy. {}%", method, extracted, EXPECTED_CARD_FIELDS, Math.round(avg));

            return OcrExtractionResult.builder()
                    .fields(fields)
                    .averageConfidence(avg)
                    .rawText("Extraction " + method + " — " + extracted + "/" + EXPECTED_CARD_FIELDS + " champs")
                    .success(hasRequired)
                    .errorMessage(hasRequired ? null : buildPartialMessage(extracted))
                    .build();
        } catch (Exception e) {
            log.error("Erreur OCR", e);
            return failure("Échec OCR: " + e.getMessage());
        }
    }

    public OcrExtractionResult extractPreprocessed(byte[] imageBytes) {
        return extractFromFullTextOnly(imageBytes);
    }

    private Map<String, OcrFieldResult> extractFromFullText(byte[] rawImageBytes, double threshold) throws Exception {
        byte[] textCrop = imageProcessingService.preprocess(rawImageBytes);
        byte[] fullCard = imageProcessingService.preprocessFull(rawImageBytes);
        Map<String, OcrFieldResult> a = parseImageBytes(textCrop, threshold);
        Map<String, OcrFieldResult> b = parseImageBytes(fullCard, threshold);
        return haitianCinParser.merge(a, b);
    }

    private OcrExtractionResult extractFromFullTextOnly(byte[] imageBytes) {
        try {
            double threshold = properties.getOcr().getConfidenceThreshold();
            Map<String, OcrFieldResult> fields = parseImageBytes(imageBytes, threshold);
            return OcrExtractionResult.builder()
                    .fields(fields)
                    .averageConfidence(fields.values().stream().mapToDouble(OcrFieldResult::getConfidence).average().orElse(0))
                    .success(hasRequiredFields(fields))
                    .build();
        } catch (Exception e) {
            return failure(e.getMessage());
        }
    }

    private Map<String, OcrFieldResult> parseImageBytes(byte[] imageBytes, double threshold) throws Exception {
        String rawText = runMultiPassOcr(imageBytes);
        return haitianCinParser.parse(rawText, threshold);
    }

    private String runMultiPassOcr(byte[] imageBytes) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (image == null) throw new IllegalArgumentException("Image illisible");
        StringBuilder allText = new StringBuilder();
        for (int psm : PSM_MODES) {
            allText.append(tesseractRunner.recognize(image, psm)).append('\n');
        }
        return allText.toString();
    }

    private int countCardFields(Map<String, OcrFieldResult> fields) {
        String[] keys = {"numero_carte", "prenom", "nom", "sexe", "nationalite", "date_naissance",
                "lieu_naissance", "departement", "date_emission", "date_expiration", "nin_display"};
        int n = 0;
        for (String k : keys) {
            if (fields.containsKey(k)) n++;
        }
        return n;
    }

    private boolean hasRequiredFields(Map<String, OcrFieldResult> fields) {
        return fields.containsKey("prenom") && fields.containsKey("nom")
                && fields.containsKey("date_naissance")
                && (fields.containsKey("nin_display") || fields.containsKey("nin"));
    }

    private String buildPartialMessage(int extracted) {
        return extracted + "/" + EXPECTED_CARD_FIELDS + " champs détectés — vérifiez les champs orange avant validation.";
    }

    private OcrExtractionResult failure(String message) {
        return OcrExtractionResult.builder()
                .fields(Map.of())
                .averageConfidence(0)
                .success(false)
                .errorMessage(message)
                .build();
    }
}

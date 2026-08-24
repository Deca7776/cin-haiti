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
    private static final int EXPECTED_CARD_FIELDS = 10;

    private final CinProperties properties;
    private final HaitianCinParser haitianCinParser;
    private final LabelAnchoredOcrService labelAnchoredOcrService;
    private final CinRoiOcrService cinRoiOcrService;
    private final ImageProcessingService imageProcessingService;
    private final OcrEngine ocrEngine;

    /**
     * OCR hybride à trois niveaux : ancrage par libellé (prioritaire — localise chaque champ par
     * son étiquette bilingue, robuste au cadrage variable d'une photo à l'autre), zones ROI à
     * coordonnées fixes (secours), texte intégral (dernier recours). Chaque niveau ne remplace un
     * champ déjà trouvé que s'il est plus confiant (cf. {@link HaitianCinParser#merge}).
     *
     * <p>Les niveaux de secours ne tournent que si l'ancrage par libellé n'a pas suffi : chaque
     * champ ROI est un appel HTTP séparé vers le même service OCR, mono-worker et lié au CPU —
     * les enchaîner systématiquement (jusqu'à ~20 appels par upload) a déjà fait dépasser le
     * timeout du service sous charge lors des tests du 19/08/2026. Le libellé ne fait que 2 appels
     * (préparation + lecture pleine carte) et couvre la carte dans l'immense majorité des cas.</p>
     */
    public OcrExtractionResult extract(byte[] rawImageBytes) {
        try {
            double threshold = properties.getOcr().getConfidenceThreshold();

            Map<String, OcrFieldResult> labelFields = labelAnchoredOcrService.extract(rawImageBytes, threshold);
            boolean labelSufficient = hasRequiredFields(labelFields) && countCardFields(labelFields) >= EXPECTED_CARD_FIELDS - 1;

            Map<String, OcrFieldResult> roiFields = labelSufficient ? Map.of() : cinRoiOcrService.extract(rawImageBytes, threshold);
            Map<String, OcrFieldResult> textFields = labelSufficient ? Map.of() : extractFromFullText(rawImageBytes, threshold);

            Map<String, OcrFieldResult> fields = haitianCinParser.merge(labelFields, roiFields);
            fields = haitianCinParser.merge(fields, textFields);
            int extracted = countCardFields(fields);
            double avg = fields.values().stream().mapToDouble(OcrFieldResult::getConfidence).average().orElse(0);
            boolean hasRequired = hasRequiredFields(fields);

            String method = labelSufficient ? "libellé" : !labelFields.isEmpty() ? "libellé+ROI+texte" : roiFields.size() >= textFields.size() ? "ROI+texte" : "texte+ROI";
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
            String text = ocrEngine.recognize(image, psm);
            allText.append(text).append('\n');
            if (!text.isBlank()) {
                // Le moteur PaddleOCR distant (prioritaire) ignore totalement le PSM : refaire
                // ce meme appel avec un autre mode renverrait exactement le meme resultat, pour
                // un cout de plusieurs secondes par tentative. On ne boucle sur les autres PSM
                // (utiles uniquement pour le repli Tesseract local) que si la passe precedente
                // n'a rien produit.
                break;
            }
        }
        return allText.toString();
    }

    private int countCardFields(Map<String, OcrFieldResult> fields) {
        String[] keys = {"numero_carte", "prenom", "nom", "sexe", "nationalite", "date_naissance",
                "lieu_naissance", "date_emission", "date_expiration", "nin_display"};
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

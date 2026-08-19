package ht.oni.cin.application.service;

import ht.oni.cin.domain.model.OcrFieldResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Extraction OCR par ancrage sur les libellés bilingues de la CIN ONI, plutôt que par
 * coordonnées ROI fixes (cf. {@link CinRoiOcrService}).
 *
 * <p>Une carte scannée proprement et une photo prise à main levée (carte dans une pochette
 * plastique, angle, zoom) ne cadrent jamais la carte de la même façon d'une capture à l'autre —
 * deux recalibrages successifs des coordonnées ROI fixes ont chacun corrigé l'échantillon qui
 * avait servi à les mesurer et cassé sur la photo suivante. Ici on localise chaque libellé
 * ("Prénom / Non", "Sexe / Sèks"...) dans le texte détecté par un OCR pleine carte, puis on lit
 * la valeur sur la ou les lignes juste en dessous, dans la même colonne (alignement sur le bord
 * gauche du libellé) — une position qui se déplace avec le cadrage réel de la photo au lieu
 * d'une fraction figée.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LabelAnchoredOcrService {

    private record FieldLabel(String key, Pattern matcher) {}

    /*
     * Chaque regle identifie la ligne de libelle d'un champ dans le texte OCR pleine carte
     * (majuscules, accents retires). Les mots-cles sont choisis pour ne collisionner avec aucun
     * autre libelle de la carte (ex: "NAISSANCE" seul matcherait a la fois "Date de Naissance" et
     * "Lieu de Naissance" -> on exige en plus "DATE" ou "LIEU" pour trancher).
     */
    private static final List<FieldLabel> FIELD_LABELS = List.of(
            new FieldLabel("numero_carte", Pattern.compile("(?=.*NUMERO)(?=.*CARTE).*")),
            new FieldLabel("prenom", Pattern.compile(".*\\bPRENOM\\b.*")),
            new FieldLabel("nom", Pattern.compile("(?!.*TITULAIRE)(?!.*PRENOM).*\\bNOM\\b.*")),
            new FieldLabel("sexe", Pattern.compile(".*\\bSEXE\\b.*")),
            new FieldLabel("nationalite", Pattern.compile(".*NATIONALITE.*")),
            new FieldLabel("lieu_naissance", Pattern.compile("(?=.*LIEU)(?=.*NAISSANCE).*")),
            new FieldLabel("date_naissance", Pattern.compile("(?!.*LIEU)(?=.*DATE)(?=.*NAISSANCE).*")),
            new FieldLabel("date_emission", Pattern.compile(".*EMISSION.*")),
            new FieldLabel("date_expiration", Pattern.compile(".*EXPIRATION.*")),
            new FieldLabel("nin_display", Pattern.compile("(?=.*IDENTIFICATION)(?=.*UNIQUE).*"))
    );

    /** Distance verticale max (fraction de hauteur carte) entre un libelle et sa valeur avant
     * d'abandonner la recherche — au-dela, on a probablement depasse la rangee suivante. */
    private static final double MAX_VALUE_GAP = 0.16;
    /** Tolerance d'alignement horizontal (bord gauche) entre libelle et valeur. */
    private static final double COLUMN_TOLERANCE = 0.12;
    /** Nombre de lignes candidates sous le libelle a essayer avant d'abandonner (gere les
     * libelles bilingues etales sur 2 lignes, ex: "Date d'emission /" puis "Dat kat la fet"). */
    private static final int MAX_CANDIDATES = 3;

    private final OcrEngine ocrEngine;
    private final ImageProcessingService imageProcessingService;

    public Map<String, OcrFieldResult> extract(byte[] rawImageBytes, double threshold) {
        Map<String, OcrFieldResult> fields = new HashMap<>();
        try {
            BufferedImage card = imageProcessingService.prepareCardImage(rawImageBytes);
            if (card == null) return fields;

            BufferedImage normalized = ocrEngine.prepareCard(card);
            List<OcrLine> lines = ocrEngine.layout(normalized);
            if (lines.isEmpty()) return fields;

            for (FieldLabel field : FIELD_LABELS) {
                findValue(field, lines).ifPresent(found ->
                        fields.put(field.key(), OcrFieldResult.of(field.key(), found.value(), found.confidence(), threshold)));
            }

            if (fields.containsKey("nin_display") && !fields.containsKey("nin")) {
                String digits = fields.get("nin_display").getValue().replaceAll("\\D", "");
                if (!digits.isEmpty()) {
                    fields.put("nin", OcrFieldResult.of("nin", CinFieldValueParser.normalizeNin(digits),
                            fields.get("nin_display").getConfidence(), threshold));
                }
            }
            if (fields.containsKey("lieu_naissance")) {
                extractDepartementFromLieu(fields, threshold);
            }
        } catch (Exception e) {
            log.warn("Extraction par libelle echouee: {}", e.getMessage());
        }
        return fields;
    }

    private record FoundValue(String value, double confidence) {}

    private java.util.Optional<FoundValue> findValue(FieldLabel field, List<OcrLine> lines) {
        OcrLine label = lines.stream()
                .filter(l -> field.matcher().matcher(normalize(l.text())).matches())
                .findFirst()
                .orElse(null);
        if (label == null) return java.util.Optional.empty();

        List<OcrLine> candidates = lines.stream()
                .filter(l -> l != label)
                .filter(l -> l.y0() > label.y1() - 0.01)
                .filter(l -> l.y0() - label.y1() < MAX_VALUE_GAP)
                .filter(l -> Math.abs(l.x0() - label.x0()) < COLUMN_TOLERANCE)
                .sorted((a, b) -> Double.compare(a.y0(), b.y0()))
                .limit(MAX_CANDIDATES)
                .toList();

        for (OcrLine candidate : candidates) {
            String value = CinFieldValueParser.clean(field.key(), candidate.text());
            if (value != null && !value.isBlank()) {
                return java.util.Optional.of(new FoundValue(value, candidate.confidence()));
            }
        }
        return java.util.Optional.empty();
    }

    private void extractDepartementFromLieu(Map<String, OcrFieldResult> fields, double threshold) {
        String lieu = fields.get("lieu_naissance").getValue();
        for (String dept : HaitianCinParser.DEPARTEMENTS) {
            if (lieu.toLowerCase().contains(dept.toLowerCase())) {
                fields.put("departement", OcrFieldResult.of("departement", dept, 86, threshold));
                return;
            }
        }
    }

    /** Majuscules + accents retires, pour un matching de libelle insensible a la casse et aux
     * variations d'accentuation introduites par l'OCR. */
    private static String normalize(String text) {
        if (text == null) return "";
        String upper = text.toUpperCase();
        String decomposed = Normalizer.normalize(upper, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "");
    }
}

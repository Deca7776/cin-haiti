package ht.oni.cin.application.service;

import ht.oni.cin.domain.model.OcrFieldResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    /** Distance verticale max entre deux lignes d'une meme valeur multi-lignes (fraction de
     * hauteur carte) avant de considerer qu'on a quitte ce bloc de valeur. */
    private static final double LINE_CONTINUATION_GAP = 0.05;
    /** Champs dont la valeur peut s'etaler sur plusieurs lignes sous le libelle (ex: un prenom
     * compose imprime "MARIE" puis "CLAUDE" sur 2 lignes) : toutes les lignes du bloc doivent
     * etre concatenees plutot que de ne garder que la premiere. */
    private static final Set<String> MULTI_LINE_FIELDS = Set.of("prenom", "nom");

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
                .filter(l -> !isAnotherFieldLabel(field, l.text()))
                .sorted((a, b) -> Double.compare(a.y0(), b.y0()))
                .limit(MAX_CANDIDATES)
                .toList();

        if (MULTI_LINE_FIELDS.contains(field.key())) {
            java.util.Optional<FoundValue> joined = joinContinuationLines(field, label, candidates);
            if (joined.isPresent()) return joined;
        }

        for (OcrLine candidate : candidates) {
            String value = CinFieldValueParser.clean(field.key(), candidate.text());
            if (value != null && !value.isBlank()) {
                return java.util.Optional.of(new FoundValue(value, candidate.confidence()));
            }
        }
        return java.util.Optional.empty();
    }

    /** Concatene les lignes consecutives sous le libelle tant qu'elles restent collees les unes
     * aux autres (meme bloc de valeur) — un prenom ou nom compose est parfois imprime sur 2
     * lignes sous son libelle bilingue ; s'arreter au premier candidat tronquait ces cas a un
     * seul mot (ex: "MARIE" au lieu de "MARIE CLAUDE"). */
    private java.util.Optional<FoundValue> joinContinuationLines(FieldLabel field, OcrLine label, List<OcrLine> candidates) {
        List<String> parts = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        double previousY1 = label.y1();
        for (OcrLine candidate : candidates) {
            if (candidate.y0() - previousY1 > LINE_CONTINUATION_GAP) break;
            String value = CinFieldValueParser.clean(field.key(), candidate.text());
            if (value == null || value.isBlank()) {
                if (parts.isEmpty()) continue;
                break;
            }
            parts.add(value);
            scores.add(candidate.confidence());
            previousY1 = candidate.y1();
        }
        if (parts.isEmpty()) return java.util.Optional.empty();
        String combined = String.join(" ", parts);
        double avgConfidence = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return java.util.Optional.of(new FoundValue(combined, avgConfidence));
    }

    /** Exclut de la recherche de valeur toute ligne qui est elle-meme le libelle d'un autre champ
     * (ex: "Nom / Siyati" juste sous "Prénom / Non") — sans ce garde-fou, un bloc multi-lignes
     * pourrait deborder sur le champ suivant. */
    private boolean isAnotherFieldLabel(FieldLabel field, String text) {
        String normalized = normalize(text);
        for (FieldLabel other : FIELD_LABELS) {
            if (other.key().equals(field.key())) continue;
            if (other.matcher().matcher(normalized).matches()) return true;
        }
        return false;
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

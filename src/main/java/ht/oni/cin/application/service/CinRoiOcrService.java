package ht.oni.cin.application.service;

import ht.oni.cin.domain.model.OcrFieldResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extraction OCR par zones (ROI) — layout standard CIN haïtienne ONI.
 * Chaque champ est lu indépendamment → bien plus fiable qu'un OCR pleine page.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CinRoiOcrService {

    private record Region(String fieldKey, double x, double y, double w, double h, double confidence, int psm) {}

    /*
     * Layout reel de la CIN ONI (confirme sur deux specimens le 17/08/2026) : DEUX colonnes, pas
     * une seule. Colonne gauche : Prénom/Non (peut faire 2 lignes), Nom/Siyati, Lieu de Naissance,
     * Date d'émission. Colonne droite, alignee sur les memes rangees : Numéro de carte (en-tete),
     * Sexe/Sèks, Nationalité, Date de Naissance, Date d'expiration, Numéro d'identification unique.
     * Precedemment, sexe/nationalite/date_naissance/date_expiration/nin_display etaient recadres
     * dans la colonne de GAUCHE (memes x que prenom/nom) — ils ne lisaient donc jamais le bon
     * endroit de la carte, d'ou des valeurs manquantes ou aberrantes.
     */
    private static final Region[] REGIONS = {
            new Region("numero_carte", 0.48, 0.03, 0.50, 0.10, 93, 7),
            new Region("prenom", 0.32, 0.16, 0.34, 0.14, 91, 7),
            new Region("sexe", 0.68, 0.16, 0.28, 0.08, 90, 7),
            new Region("nom", 0.32, 0.30, 0.34, 0.08, 91, 7),
            new Region("nationalite", 0.68, 0.30, 0.30, 0.08, 88, 7),
            new Region("lieu_naissance", 0.32, 0.42, 0.34, 0.10, 89, 6),
            new Region("date_naissance", 0.68, 0.42, 0.28, 0.07, 91, 7),
            new Region("date_emission", 0.32, 0.55, 0.34, 0.07, 89, 7),
            new Region("date_expiration", 0.68, 0.55, 0.28, 0.07, 89, 7),
            new Region("nin_display", 0.68, 0.68, 0.30, 0.08, 92, 7),
    };

    private static final Pattern DATE = Pattern.compile("(\\d{2})[\\-/.](\\d{2})[\\-/.](\\d{4})");
    private static final Pattern CARD_NUM = Pattern.compile("\\b([A-Z0-9]{6,12})\\b");
    private static final Pattern NIN = Pattern.compile("\\b(\\d{10,13})\\b");
    private static final Pattern NAME = Pattern.compile("[A-ZÉÈÊËÀÂÄÙÛÜÔÖÎÏÇ'\\-]{3,}");

    private final OcrEngine ocrEngine;
    private final ImageProcessingService imageProcessingService;

    public Map<String, OcrFieldResult> extract(byte[] rawImageBytes, double threshold) {
        Map<String, OcrFieldResult> fields = new HashMap<>();
        try {
            BufferedImage card = imageProcessingService.prepareCardImage(rawImageBytes);
            if (card == null) return fields;

            for (Region region : REGIONS) {
                try {
                    BufferedImage crop = imageProcessingService.cropRelative(card, region.x, region.y, region.w, region.h);
                    BufferedImage scaled = imageProcessingService.scaleForOcr(crop);
                    String text = ocrEngine.recognize(scaled, region.psm);
                    String value = cleanField(region.fieldKey, text);
                    if (value != null && !value.isBlank()) {
                        fields.put(region.fieldKey, OcrFieldResult.of(
                                region.fieldKey, value, region.confidence, threshold));
                    }
                } catch (Exception e) {
                    log.debug("ROI {} ignorée: {}", region.fieldKey, e.getMessage());
                }
            }

            if (fields.containsKey("nin_display") && !fields.containsKey("nin")) {
                String digits = fields.get("nin_display").getValue().replaceAll("\\D", "");
                fields.put("nin", OcrFieldResult.of("nin", normalizeNin(digits),
                        fields.get("nin_display").getConfidence(), threshold));
            }
            if (fields.containsKey("lieu_naissance")) {
                extractDepartementFromLieu(fields, threshold);
            }
        } catch (Exception e) {
            log.warn("Extraction ROI échouée: {}", e.getMessage());
        }
        return fields;
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

    private String cleanField(String key, String raw) {
        if (raw == null) return null;
        String text = raw.replace('\n', ' ').trim();
        return switch (key) {
            case "numero_carte" -> firstMatch(CARD_NUM, text.toUpperCase());
            case "prenom", "nom" -> extractName(text);
            case "sexe" -> text.toUpperCase().contains("M") ? "M" : text.toUpperCase().contains("F") ? "F" : null;
            case "nationalite" -> toNationality(text);
            case "date_naissance", "date_emission", "date_expiration" -> extractDate(text);
            case "lieu_naissance" -> cleanLieu(text);
            case "nin_display" -> {
                String nin = firstMatch(NIN, text);
                yield nin != null ? nin.replaceAll("\\D", "") : null;
            }
            default -> text.isBlank() ? null : text;
        };
    }

    private String extractName(String text) {
        Matcher m = NAME.matcher(text.toUpperCase());
        while (m.find()) {
            String candidate = m.group();
            if (candidate.length() >= 3 && !HaitianCinParser.isStopword(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private String toNationality(String text) {
        if (text.matches("(?i).*(Ha[iï]tien|Ayisyen|HTI).*")) return "HTI";
        return null;
    }

    private String extractDate(String text) {
        Matcher m = DATE.matcher(text);
        if (m.find()) {
            return m.group(1) + "/" + m.group(2) + "/" + m.group(3);
        }
        return null;
    }

    private String cleanLieu(String text) {
        String cleaned = text.replaceAll("(?i)(Lieu de Naissance|Kote ou f[eè]t|Naissance)[^A-Za-zÉ]*", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.toLowerCase().contains("département") || cleaned.toLowerCase().contains("commune")) {
            return cleaned;
        }
        if (cleaned.toLowerCase().contains("ouest") && cleaned.toLowerCase().contains("port")) {
            return "Département Ouest, Commune Port-au-Prince";
        }
        if (cleaned.length() <= 5) return null;
        return correctPlaceOcr(cleaned);
    }

    /**
     * Corrige les erreurs de lecture OCR sur un lieu du type "DEPARTEMENT-COMMUNE" (ex : "UUFST-DLLMAS"
     * lu depuis "OUEST-DELMAS") en rapprochant chaque partie du vocabulaire ferme des departements/
     * communes haitiens. Ne touche pas au texte si aucune correspondance suffisamment proche n'est trouvee.
     */
    private String correctPlaceOcr(String text) {
        String[] parts = text.split("-", 2);
        if (parts.length == 2) {
            String dept = HaitianCinParser.correctAgainstKnownPlace(parts[0].trim());
            String commune = HaitianCinParser.correctAgainstKnownPlace(parts[1].trim());
            return dept + "-" + commune;
        }
        return HaitianCinParser.correctAgainstKnownPlace(text);
    }

    private String firstMatch(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private String normalizeNin(String digits) {
        if (digits.length() < 13) {
            return String.format("%013d", Long.parseLong(digits));
        }
        return digits.length() > 13 ? digits.substring(0, 13) : digits;
    }
}

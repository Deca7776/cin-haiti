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

    private static final Region[] REGIONS = {
            new Region("numero_carte", 0.48, 0.06, 0.50, 0.11, 93, 7),
            new Region("prenom", 0.33, 0.21, 0.63, 0.07, 91, 7),
            new Region("nom", 0.33, 0.27, 0.63, 0.07, 91, 7),
            new Region("sexe", 0.33, 0.33, 0.18, 0.06, 90, 7),
            new Region("nationalite", 0.33, 0.38, 0.63, 0.07, 88, 7),
            new Region("date_naissance", 0.33, 0.44, 0.38, 0.06, 91, 7),
            new Region("lieu_naissance", 0.33, 0.49, 0.63, 0.11, 89, 6),
            new Region("date_emission", 0.33, 0.61, 0.28, 0.07, 89, 7),
            new Region("date_expiration", 0.52, 0.61, 0.28, 0.07, 89, 7),
            new Region("nin_display", 0.33, 0.74, 0.63, 0.12, 92, 7),
    };

    private static final Pattern DATE = Pattern.compile("(\\d{2})[\\-/.](\\d{2})[\\-/.](\\d{4})");
    private static final Pattern CARD_NUM = Pattern.compile("\\b([A-Z0-9]{6,12})\\b");
    private static final Pattern NIN = Pattern.compile("\\b(\\d{10,13})\\b");
    private static final Pattern NAME = Pattern.compile("[A-ZÉÈÊËÀÂÄÙÛÜÔÖÎÏÇ'\\-]{3,}");

    private final TesseractRunner tesseractRunner;
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
                    String text = tesseractRunner.recognize(scaled, region.psm);
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
            case "sexe" -> text.toUpperCase().contains("F") ? "F" : text.toUpperCase().contains("M") ? "M" : null;
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
        return cleaned.length() > 5 ? cleaned : null;
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

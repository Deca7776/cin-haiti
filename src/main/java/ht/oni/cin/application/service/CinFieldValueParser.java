package ht.oni.cin.application.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nettoyage/validation d'une valeur de champ CIN à partir du texte brut OCR d'une ligne ou
 * d'une zone. Partagé entre {@link CinRoiOcrService} (zones à coordonnées fixes) et
 * {@link LabelAnchoredOcrService} (valeur localisée sous le libellé bilingue détecté) afin que
 * les deux stratégies d'extraction valident les champs de la même façon.
 */
final class CinFieldValueParser {

    private static final Pattern DATE = Pattern.compile("(\\d{2})[\\-/.](\\d{2})[\\-/.](\\d{4})");
    private static final Pattern CARD_NUM = Pattern.compile("\\b([A-Z0-9]{6,12})\\b");
    private static final Pattern NIN = Pattern.compile("\\b(\\d{10,13})\\b");
    private static final Pattern NAME = Pattern.compile("[A-ZÉÈÊËÀÂÄÙÛÜÔÖÎÏÇ'\\-]{3,}");

    private CinFieldValueParser() {
    }

    static String clean(String key, String raw) {
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

    private static String extractName(String text) {
        Matcher m = NAME.matcher(text.toUpperCase());
        while (m.find()) {
            String candidate = m.group();
            if (candidate.length() >= 3 && !HaitianCinParser.isStopword(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static String toNationality(String text) {
        if (text.matches("(?i).*(Ha[iï]tien|Ayisyen|HTI).*")) return "HTI";
        return null;
    }

    private static String extractDate(String text) {
        Matcher m = DATE.matcher(text);
        if (m.find()) {
            return m.group(1) + "/" + m.group(2) + "/" + m.group(3);
        }
        return null;
    }

    private static final Pattern DEPT_COMMUNE = Pattern.compile(
            "(?i)D[eé]partement\\s+([A-Za-zÉÈÊËÀÂÄÙÛÜÔÖÎÏÇ'\\- ]+?),\\s*Commune\\s+(.+)$");

    private static String cleanLieu(String text) {
        String cleaned = text.replaceAll("(?i)(Lieu de Naissance|Kote ou f[eè]t|Naissance)[^A-Za-zÉ]*", "")
                .replaceAll("\\s+", " ")
                .trim();
        Matcher deptCommune = DEPT_COMMUNE.matcher(cleaned);
        if (deptCommune.find()) {
            // Le departement est recalcule a partir de la commune (bibliotheque de lieux fiable)
            // plutot que garde tel que lu par l'OCR — cf. HaitianCinParser.departementForCommune.
            return HaitianCinParser.canonicalizeLieu(deptCommune.group(1), deptCommune.group(2));
        }
        if (cleaned.toLowerCase().contains("ouest") && cleaned.toLowerCase().contains("port")) {
            return "Département Ouest, Commune Port-au-Prince";
        }
        if (cleaned.length() <= 5) return null;
        return correctPlaceOcr(cleaned);
    }

    /**
     * Corrige les erreurs de lecture OCR sur un lieu du type "DEPARTEMENT-COMMUNE" (ex : "UUFST-DLLMAS"
     * lu depuis "OUEST-DELMAS") en rapprochant la commune du vocabulaire ferme et en en deduisant le
     * departement via la bibliotheque de lieux — plus fiable que de corriger le mot departement lui-meme,
     * plus court et donc plus ambigu face aux erreurs OCR. Ne touche pas au texte si aucune correspondance
     * suffisamment proche n'est trouvee.
     */
    private static String correctPlaceOcr(String text) {
        String[] parts = text.split("-", 2);
        if (parts.length == 2) {
            String commune = HaitianCinParser.correctAgainstKnownPlace(parts[1].trim(), HaitianCinParser.COMMUNES);
            String dept = HaitianCinParser.departementForCommune(commune);
            if (dept == null) {
                dept = HaitianCinParser.correctAgainstKnownPlace(parts[0].trim(), HaitianCinParser.DEPARTEMENTS);
            }
            return dept + "-" + commune;
        }
        return HaitianCinParser.correctAgainstKnownPlace(text);
    }

    private static String firstMatch(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    static String normalizeNin(String digits) {
        if (digits.length() < 13) {
            return String.format("%013d", Long.parseLong(digits));
        }
        return digits.length() > 13 ? digits.substring(0, 13) : digits;
    }
}

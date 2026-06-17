package ht.oni.cin.application.service;

import ht.oni.cin.domain.model.OcrFieldResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parseur OCR spécifique à la CIN haïtienne (format ONI bilingue FR/HT).
 * Exemple : Patricia Delaire — T1K2N89G7 — Département Ouest, Port-au-Prince.
 */
@Slf4j
@Component
public class HaitianCinParser {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static final List<String> DEPARTEMENTS = List.of(
            "Artibonite", "Centre", "Grand'Anse", "Nippes", "Nord", "Nord-Est",
            "Nord-Ouest", "Ouest", "Sud", "Sud-Est"
    );

    private static final Pattern DATE = Pattern.compile("(?<![\\d])(?:-)?(\\d{2})[\\-/.](\\d{2})[\\-/.](\\d{4})\\b");

    public static boolean isStopword(String upper) {
        return NAME_STOPWORDS.contains(upper);
    }

    private static final Set<String> NAME_STOPWORDS = Set.of(
            "HAITI", "HAÏTI", "REPIBLIK", "REPUBLIQUE", "CARTE", "IDENTIFICATION", "NATIONALE", "NASYONAL",
            "NATIONALITE", "NASYONALITE", "HARTIEN", "HAÏTIEN", "AYISYEN", "OUEST", "COMMUNE", "PORT",
            "PRINCE", "DEPARTEMENT", "SIGNATURE", "NUMERO", "NIMERO", "RNCS", "CPP", "SYON", "NASY", "NAISSANCE",
            "SEXE", "SEKS", "SIYATI"
    );

    public Map<String, OcrFieldResult> parse(String rawText, double threshold) {
        Map<String, OcrFieldResult> fields = new HashMap<>();
        if (rawText == null || rawText.isBlank()) {
            return fields;
        }

        String text = rawText.replace('\r', '\n');
        String flat = text.replace('\n', ' ');

        extractNumeroCarte(text, flat, threshold, fields);
        extractByLabel(text, flat, "(?i)Pr[eé]nom\\s*/\\s*Non", "prenom", 90, threshold, fields);
        extractByLabel(text, flat, "(?i)Nom\\s*/\\s*Siyati", "nom", 90, threshold, fields);
        extractSexe(text, flat, threshold, fields);
        extractNationalite(flat, threshold, fields);
        extractNin(text, flat, threshold, fields);
        extractLieuNaissance(text, flat, threshold, fields);
        extractDepartement(text, flat, threshold, fields);
        extractDatesByContext(text, threshold, fields);

        if (!fields.containsKey("nom") || !fields.containsKey("prenom")) {
            extractNamesFuzzy(text, flat, threshold, fields);
        }
        if (!fields.containsKey("nom") || !fields.containsKey("prenom")) {
            extractNamesFromLines(text, threshold, fields);
        }

        return fields;
    }

    public Map<String, OcrFieldResult> merge(Map<String, OcrFieldResult> base, Map<String, OcrFieldResult> extra) {
        Map<String, OcrFieldResult> merged = new HashMap<>(base);
        extra.forEach((key, value) -> {
            OcrFieldResult existing = merged.get(key);
            if (existing == null || existing.getConfidence() < value.getConfidence()) {
                merged.put(key, value);
            }
        });
        return merged;
    }

    public int scoreCompleteness(Map<String, OcrFieldResult> fields) {
        String[] expected = {
                "numero_carte", "prenom", "nom", "sexe", "nationalite", "date_naissance",
                "lieu_naissance", "departement", "date_emission", "date_expiration", "nin", "nin_display"
        };
        int score = 0;
        for (String key : expected) {
            if (fields.containsKey(key)) score++;
        }
        return score;
    }

    private void extractNumeroCarte(String text, String flat, double threshold, Map<String, OcrFieldResult> fields) {
        Pattern labeled = Pattern.compile(
                "(?i)(?:Num[eé]ro de carte|Nimewo kat(?:\\s+la|ia)?)\\s*:?\\s*([A-Z0-9]{6,12})");
        Matcher m = labeled.matcher(flat);
        if (m.find()) {
            put(fields, "numero_carte", m.group(1).toUpperCase(), 92, threshold);
            return;
        }
        for (String line : text.split("\n")) {
            Matcher lineMatch = Pattern.compile("\\b([A-Z][A-Z0-9]{7,11})\\b").matcher(line.trim());
            while (lineMatch.find()) {
                String candidate = lineMatch.group(1);
                if (!candidate.matches("\\d+") && !candidate.contains("IDENT")) {
                    put(fields, "numero_carte", candidate, 78, threshold);
                    return;
                }
            }
        }
    }

    private void extractNationalite(String flat, double threshold, Map<String, OcrFieldResult> fields) {
        Pattern labeled = Pattern.compile(
                "(?i)Nationalit[eé]\\s*/\\s*Nasyonalite\\s*:?\\s*(Ha[iï]tien(?:ne)?|Ayisyen|HTI)");
        Matcher m = labeled.matcher(flat);
        if (m.find()) {
            put(fields, "nationalite", toNationalityCode(m.group(1)), 91, threshold);
            return;
        }
        if (flat.matches("(?i).*(Ha[iï]tien(?:ne)?|Ayisyen).*")) {
            put(fields, "nationalite", "HTI", 80, threshold);
        }
    }

    private String toNationalityCode(String raw) {
        if (raw == null) return "HTI";
        String u = raw.toUpperCase();
        if ("HTI".equals(u)) return "HTI";
        return "HTI";
    }

    private void extractNamesFuzzy(String text, String flat, double threshold, Map<String, OcrFieldResult> fields) {
        if (!fields.containsKey("prenom")) {
            Pattern prenomZone = Pattern.compile("(?i)Pr[eé]nom.{0,120}?\\b([A-ZÉÈÊËÀÂÄÙÛÜÔÖÎÏÇ'\\-]{4,})\\b");
            Matcher m = prenomZone.matcher(flat);
            while (m.find()) {
                if (putName(fields, "prenom", m.group(1), 76, threshold)) {
                    break;
                }
            }
        }
        if (!fields.containsKey("prenom")) {
            Pattern afterSeks = Pattern.compile("(?i)S[eè]ks\\s+([A-ZÉÈÊËÀÂÄÙÛÜÔÖÎÏÇ'\\-]{4,})");
            Matcher m = afterSeks.matcher(flat);
            if (m.find()) {
                putName(fields, "prenom", m.group(1), 73, threshold);
            }
        }
        if (!fields.containsKey("prenom")) {
            for (String line : text.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.matches("^[A-ZÉÈÊËÀÂÄÙÛÜÔÖÎÏÇ'\\-]{4,}(\\s+[A-ZÉ]{2,})?$")) {
                    String first = trimmed.split("\\s+")[0];
                    if (isValidPersonName(first)) {
                        putName(fields, "prenom", first, 74, threshold);
                        break;
                    }
                }
            }
        }
        if (!fields.containsKey("nom")) {
            Pattern nomZone = Pattern.compile("(?i)Siyati.{0,80}?\\b([A-ZÉÈÊËÀÂÄÙÛÜÔÖÎÏÇ'\\-]{4,})\\b");
            Matcher m = nomZone.matcher(flat);
            while (m.find()) {
                if (putName(fields, "nom", m.group(1), 74, threshold)) {
                    break;
                }
            }
        }
        if (!fields.containsKey("nom")) {
            List<String> candidates = findNameCandidates(text);
            for (String candidate : candidates) {
                if (!fields.containsKey("prenom") && putName(fields, "prenom", candidate, 70, threshold)) {
                    continue;
                }
                if (!fields.containsKey("nom") && putName(fields, "nom", candidate, 72, threshold)) {
                    break;
                }
            }
        }
    }

    private List<String> findNameCandidates(String text) {
        Set<String> seen = new LinkedHashSet<>();
        for (String line : text.split("\n")) {
            for (String token : line.trim().split("\\s+")) {
                String cleaned = token.replaceAll("[^A-Za-zÉÈÊËÀÂÄÙÛÜÔÖÎÏÇ'\\-]", "");
                if (isValidPersonName(cleaned)) {
                    seen.add(cleaned.toUpperCase());
                }
            }
        }
        return new ArrayList<>(seen);
    }

    private void extractByLabel(String text, String flat, String labelPattern, String fieldKey,
                                double confidence, double threshold, Map<String, OcrFieldResult> fields) {
        if (fields.containsKey(fieldKey)) return;

        Pattern lineAfter = Pattern.compile(labelPattern + "[^\\n]*\\n\\s*([A-ZÉÈÊËÀÂÄÙÛÜÔÖÎÏÇ'\\- ]{2,})");
        Matcher m2 = lineAfter.matcher(text);
        if (m2.find()) {
            String name = firstNameToken(cleanName(m2.group(1)));
            if (putName(fields, fieldKey, name, confidence, threshold)) {
                return;
            }
        }

        Pattern afterLabelSameLine = Pattern.compile(
                labelPattern + "\\s*:?\\s*([A-ZÉÈÊËÀÂÄÙÛÜÔÖÎÏÇ'\\-]+(?:\\s+[A-ZÉÈÊËÀÂÄÙÛÜÔÖÎÏÇ'\\-]+){0,2})");
        Matcher m1 = afterLabelSameLine.matcher(flat);
        if (m1.find()) {
            putName(fields, fieldKey, firstNameToken(cleanName(m1.group(1))), confidence - 5, threshold);
        }
    }

    private void extractSexe(String text, String flat, double threshold, Map<String, OcrFieldResult> fields) {
        Pattern p = Pattern.compile("(?i)Sexe\\s*/\\s*S[eè]ks\\s*:?\\s*(M|F)\\b");
        Matcher m = p.matcher(flat);
        if (m.find()) {
            put(fields, "sexe", m.group(1).toUpperCase(), 92, threshold);
            return;
        }
        Pattern p2 = Pattern.compile("(?i)S[eè]ks[^\\n]{0,80}?\\b(M|F)\\b");
        m = p2.matcher(text.replace('\n', ' '));
        if (m.find()) {
            put(fields, "sexe", m.group(1).toUpperCase(), 85, threshold);
            return;
        }
        Pattern isolated = Pattern.compile("(?i)(?:^|\\s)(M|F)(?:\\s|$)");
        m = isolated.matcher(flat);
        while (m.find()) {
            put(fields, "sexe", m.group(1).toUpperCase(), 70, threshold);
            return;
        }
    }

    private void extractNin(String text, String flat, double threshold, Map<String, OcrFieldResult> fields) {
        Pattern labeled = Pattern.compile(
                "(?i)(?:Num[eé]ro d'identification unique|Nimewo idantifikasyon inik|NIN|RNCS)\\s*[=:]?\\s*(\\d{10,13})");
        Matcher m = labeled.matcher(flat);
        if (m.find()) {
            storeNin(fields, m.group(1), 93, threshold);
            return;
        }
        Matcher m13 = Pattern.compile("\\b(\\d{13})\\b").matcher(flat);
        if (m13.find()) {
            storeNin(fields, m13.group(1), 80, threshold);
            return;
        }
        Matcher m10 = Pattern.compile("\\b(\\d{10})\\b").matcher(flat);
        if (m10.find()) {
            storeNin(fields, m10.group(1), 75, threshold);
        }
    }

    private void storeNin(Map<String, OcrFieldResult> fields, String raw, double confidence, double threshold) {
        String digits = raw.replaceAll("\\D", "");
        put(fields, "nin_display", digits, confidence, threshold);
        put(fields, "nin", normalizeNin(digits), confidence, threshold);
    }

    private String normalizeNin(String raw) {
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() < 13) {
            return String.format("%013d", Long.parseLong(digits));
        }
        return digits.length() > 13 ? digits.substring(0, 13) : digits;
    }

    private void extractLieuNaissance(String text, String flat, double threshold, Map<String, OcrFieldResult> fields) {
        Pattern p = Pattern.compile(
                "(?i)(?:Lieu de Naissance|Kote ou f[eè]t|Naissance\\s*/\\s*Kote)[^\\n]{0,40}\\n?\\s*(.+?)(?=\\s+Date d|$)",
                Pattern.DOTALL);
        Matcher m = p.matcher(flat);
        if (m.find()) {
            String lieu = cleanLieu(m.group(1));
            if (lieu.length() > 5) {
                put(fields, "lieu_naissance", lieu, 85, threshold);
            }
        }
        Pattern deptCommune = Pattern.compile(
                "(?i)D[eé]partement\\s+([A-Za-zÉÈÊËÀÂÄÙÛÜÔÖÎÏÇ'\\- ]+?),\\s*Commune\\s+(.+?)(?=\\s+Date|$)");
        m = deptCommune.matcher(flat);
        if (m.find()) {
            String lieu = "Département " + m.group(1).trim() + ", Commune " + cleanLieu(m.group(2));
            put(fields, "lieu_naissance", lieu, 88, threshold);
            return;
        }
        Pattern messyLieu = Pattern.compile(
                "(?i)(?:sr\\s+)?(?:D[eé]partement\\s+)?(Ouest|Artibonite|Centre|Nord|Sud|Nippes|Nord-Est|Nord-Ouest|Sud-Est|Grand'Anse),\\s*Commune\\s+([A-Za-zÉÈÊËÀÂÄ'\\- ]+?)(?=\\s+Date|\\s+°|$)");
        m = messyLieu.matcher(flat);
        if (m.find()) {
            String lieu = "Département " + m.group(1).trim() + ", Commune " + cleanLieu(m.group(2));
            put(fields, "lieu_naissance", lieu, 82, threshold);
        }
    }

    private String cleanLieu(String raw) {
        return raw.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[°\"]+", "")
                .replaceAll("\\s+Date.*$", "")
                .trim();
    }

    private void extractDepartement(String text, String flat, double threshold, Map<String, OcrFieldResult> fields) {
        for (String dept : DEPARTEMENTS) {
            if (flat.toLowerCase().contains(dept.toLowerCase())) {
                put(fields, "departement", dept, 86, threshold);
                return;
            }
        }
    }

    private void extractDatesByContext(String text, double threshold, Map<String, OcrFieldResult> fields) {
        extractDateNearLabel(text, "Date de Naissance|Dat ou f[eè]t", "date_naissance", 90, threshold, fields);
        extractDateNearLabel(text, "Date d['']?[eé]mission|Dat kat la f[eè]t", "date_emission", 88, threshold, fields);
        extractDateNearLabel(text, "Date d['']?expiration|Dat kat la fini", "date_expiration", 88, threshold, fields);
        extractPairedEmissionExpiration(text, threshold, fields);

        List<String> orderedDates = findAllDates(text);
        if (orderedDates.size() >= 3) {
            put(fields, "date_naissance", orderedDates.get(0), 72, threshold);
            put(fields, "date_emission", orderedDates.get(1), 70, threshold);
            put(fields, "date_expiration", orderedDates.get(2), 70, threshold);
        } else if (!fields.containsKey("date_naissance") || !fields.containsKey("date_emission")) {
            int i = 0;
            String[] keys = {"date_naissance", "date_emission", "date_expiration"};
            for (String date : orderedDates) {
                while (i < keys.length && fields.containsKey(keys[i])) {
                    i++;
                }
                if (i >= keys.length) break;
                put(fields, keys[i], date, 68 - i * 3, threshold);
                i++;
            }
        }
    }

    private void extractPairedEmissionExpiration(String text, double threshold, Map<String, OcrFieldResult> fields) {
        Pattern pair = Pattern.compile(
                "(?:-)?(\\d{2})[\\-/.](\\d{2})[\\-/.](\\d{4})\\s+(?:-)?(\\d{2})[\\-/.](\\d{2})[\\-/.](\\d{4})");
        Matcher m = pair.matcher(text.replace('\n', ' '));
        if (m.find()) {
            put(fields, "date_emission", formatDate(m.group(1), m.group(2), m.group(3)), 82, threshold);
            put(fields, "date_expiration", formatDate(m.group(4), m.group(5), m.group(6)), 84, threshold);
        }
    }

    private List<String> findAllDates(String text) {
        List<String> dates = new ArrayList<>();
        Matcher m = DATE.matcher(text);
        while (m.find()) {
            dates.add(formatDate(m.group(1), m.group(2), m.group(3)));
        }
        return dates;
    }

    private void extractDateNearLabel(String text, String labelAlternation, String fieldKey,
                                      double confidence, double threshold, Map<String, OcrFieldResult> fields) {
        if (fields.containsKey(fieldKey)) return;

        Pattern lineAfter = Pattern.compile(
                "(?i)(?:" + labelAlternation + ")[^\\n]*\\n\\s*(\\d{2})[\\-/.](\\d{2})[\\-/.](\\d{4})");
        Matcher mLine = lineAfter.matcher(text);
        if (mLine.find()) {
            put(fields, fieldKey, formatDate(mLine.group(1), mLine.group(2), mLine.group(3)), confidence, threshold);
            return;
        }

        Pattern sameLine = Pattern.compile(
                "(?i)(?:" + labelAlternation + ")[^\\d]{0,60}(\\d{2})[\\-/.](\\d{2})[\\-/.](\\d{4})");
        Matcher m = sameLine.matcher(text.replace('\n', ' '));
        if (m.find()) {
            put(fields, fieldKey, formatDate(m.group(1), m.group(2), m.group(3)), confidence - 3, threshold);
        }
    }

    private void extractNamesFromLines(String text, double threshold, Map<String, OcrFieldResult> fields) {
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.matches("^[A-ZÉÈÊËÀÂÄÙÛÜÔÖÎÏÇ'\\-]{3,}$")
                    && !trimmed.contains("HAÏTI") && !trimmed.contains("REPIBLIK")
                    && !trimmed.contains("CARTE") && !trimmed.contains("IDENTIFICATION")) {
                if (!fields.containsKey("prenom")) {
                    putName(fields, "prenom", capitalizeWords(firstNameToken(trimmed)), 78, threshold);
                } else if (!fields.containsKey("nom")) {
                    putName(fields, "nom", firstNameToken(trimmed), 82, threshold);
                    break;
                }
            }
        }
    }

    private String formatDate(String d, String m, String y) {
        String dateStr = d + "/" + m + "/" + y;
        try {
            LocalDate.parse(dateStr, DATE_FMT);
            return dateStr;
        } catch (DateTimeParseException e) {
            return dateStr;
        }
    }

    private String cleanName(String s) {
        return s.trim()
                .replaceAll("\\s{2,}", " ")
                .replaceAll("[^A-Za-zÉÈÊËÀÂÄÙÛÜÔÖÎÏÇ'\\- ]", "")
                .replaceAll("\\s+(Nom|Siyati|Non|Sèks|Seks)$", "")
                .trim();
    }

    private String capitalizeWords(String s) {
        String[] parts = s.toLowerCase().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            }
        }
        return sb.toString();
    }

    private String firstNameToken(String s) {
        if (s == null || s.isBlank()) return s;
        return s.trim().split("\\s+")[0];
    }

    private boolean isValidPersonName(String name) {
        if (name == null || name.length() < 3) return false;
        String upper = name.toUpperCase().replaceAll("[^A-ZÉÈÊËÀÂÄÙÛÜÔÖÎÏÇ'\\-]", "");
        if (upper.length() < 3) return false;
        return !NAME_STOPWORDS.contains(upper);
    }

    private boolean putName(Map<String, OcrFieldResult> fields, String key, String value,
                            double confidence, double threshold) {
        String cleaned = firstNameToken(cleanName(value));
        if (!isValidPersonName(cleaned)) return false;
        put(fields, key, cleaned, confidence, threshold);
        return true;
    }

    private void put(Map<String, OcrFieldResult> fields, String key, String value, double confidence, double threshold) {
        if (value != null && !value.isBlank()) {
            fields.put(key, OcrFieldResult.of(key, value.trim(), confidence, threshold));
        }
    }
}

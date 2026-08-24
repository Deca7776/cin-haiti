package ht.oni.cin.application.service;

import ht.oni.cin.domain.model.OcrFieldResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    /**
     * Les 148 communes d'Haïti, regroupees par departement — source :
     * https://en.wikipedia.org/wiki/List_of_communes_of_Haiti (recupere le 17/08/2026).
     * Vocabulaire ferme utilise pour corriger les erreurs de lecture OCR par correspondance approchee,
     * et pour deriver automatiquement le departement d'une commune reconnue (cf. {@link #departementForCommune}) —
     * la carte n'a donc pas besoin d'un champ "departement" saisi/extrait a part : une fois la commune
     * identifiee dans cette bibliotheque, son departement en decoule.
     */
    public static final Map<String, List<String>> COMMUNES_BY_DEPARTEMENT = new LinkedHashMap<>();
    static {
        COMMUNES_BY_DEPARTEMENT.put("Artibonite", List.of(
                "Dessalines", "Desdunes", "Grande-Saline", "Petite Rivière de l'Artibonite", "Gonaïves",
                "Ennery", "L'Estère", "Gros-Morne", "Anse-Rouge", "Terre-Neuve", "Marmelade",
                "Saint-Michel-de-l'Atalaye", "Saint-Marc", "Les Arcadins", "La Chapelle", "Liancourt",
                "Verrettes", "Montrouis"));
        COMMUNES_BY_DEPARTEMENT.put("Centre", List.of(
                "Cerca-la-Source", "Thomassique", "Hinche", "Cerca-Carvajal", "Maïssade", "Thomonde",
                "Lascahobas", "Baptiste", "Belladère", "Savanette", "Mirebalais", "Boucan-Carré", "Saut-d'Eau"));
        COMMUNES_BY_DEPARTEMENT.put("Grand'Anse", List.of(
                "Anse-d'Hainault", "Dame-Marie", "Les Irois", "Beaumont", "Corail", "Pestel", "Roseaux",
                "Jérémie", "Abricots", "Bonbon", "Chambellan", "Marfranc", "Moron"));
        COMMUNES_BY_DEPARTEMENT.put("Nippes", List.of(
                "Anse-à-Veau", "Arnaud", "L'Asile", "Petit-Trou-de-Nippes", "Plaisance-du-Sud", "Baradères",
                "Grand-Boucan", "Miragoâne", "Fonds-des-Nègres", "Paillant", "Petite-Rivière-de-Nippes"));
        COMMUNES_BY_DEPARTEMENT.put("Nord", List.of(
                "Acul-du-Nord", "Milot", "Plaine-du-Nord", "Borgne", "Port-Margot", "Cap-Haïtien", "Limonade",
                "Quartier-Morin", "Grande-Rivière-du-Nord", "Bahon", "Limbé", "Bas-Limbé", "Plaisance",
                "Pilate", "Saint-Raphaël", "Dondon", "La Victoire", "Pignon", "Ranquitte"));
        COMMUNES_BY_DEPARTEMENT.put("Nord-Est", List.of(
                "Fort-Liberté", "Perches", "Ferrier", "Ouanaminthe", "Capotille", "Mont-Organisé",
                "Trou-du-Nord", "Caracol", "Sainte-Suzanne", "Grand-Bassin", "Terrier-Rouge", "Vallières",
                "Carice", "Mombin-Crochu"));
        COMMUNES_BY_DEPARTEMENT.put("Nord-Ouest", List.of(
                "Môle-Saint-Nicolas", "Baie-de-Henne", "Bombardopolis", "Jean-Rabel", "Port-de-Paix",
                "Bassin-Bleu", "Chansolme", "Lapointe", "La Tortue", "Saint-Louis-du-Nord", "Anse-à-Foleur"));
        COMMUNES_BY_DEPARTEMENT.put("Ouest", List.of(
                "Arcahaie", "Cabaret", "Croix-des-Bouquets", "Cornillon", "Fonds-Verrettes", "Ganthier",
                "Thomazeau", "Anse-à-Galets", "Pointe-à-Raquette", "Léogâne", "Grand-Goâve", "Petit-Goâve",
                "Port-au-Prince", "Carrefour", "Cité Soleil", "Delmas", "Gressier", "Kenscoff",
                "Pétion-Ville", "Tabarre"));
        COMMUNES_BY_DEPARTEMENT.put("Sud-Est", List.of(
                "Bainet", "Côtes-de-Fer", "Belle-Anse", "Anse-à-Pitres", "Grand-Gosier", "Thiotte",
                "Jacmel", "Cayes-Jacmel", "La Vallée", "Marigot"));
        COMMUNES_BY_DEPARTEMENT.put("Sud", List.of(
                "Aquin", "Cavaillon", "Saint-Louis-du-Sud", "Fond des Blancs", "Les Cayes", "Camp-Perrin",
                "Chantal", "Île-à-Vache", "Maniche", "Torbeck", "Chardonnières", "Les Anglais", "Tiburon",
                "Côteaux", "Port-à-Piment", "Roche-à-Bateaux", "Port-Salut", "Arniquet", "Saint-Jean-du-Sud"));
    }

    public static final List<String> COMMUNES = COMMUNES_BY_DEPARTEMENT.values().stream()
            .flatMap(List::stream)
            .toList();

    private static final Pattern DATE = Pattern.compile("(?<![\\d])(?:-)?(\\d{2})[\\-/.](\\d{2})[\\-/.](\\d{4})\\b");

    /**
     * Corrige un mot possiblement mal lu par l'OCR en le rapprochant du terme connu (departement ou
     * commune) le plus proche, si l'ecart (distance de Levenshtein) reste raisonnable par rapport a
     * la longueur du mot. Ex: "UUFST" (lu depuis "OUEST") -> "Ouest".
     */
    public static String correctAgainstKnownPlace(String word) {
        return correctAgainstKnownPlace(word, allKnownPlaces());
    }

    /** Meme correction que {@link #correctAgainstKnownPlace(String)}, restreinte a un vocabulaire
     * donne (ex: uniquement les communes) pour eviter qu'un mot mal lu ne soit rapproche a tort
     * d'un departement alors qu'on sait deja qu'on lit une commune. */
    public static String correctAgainstKnownPlace(String word, List<String> vocabulary) {
        if (word == null || word.isBlank()) return word;
        String trimmed = word.trim();
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String known : vocabulary) {
            int distance = levenshtein(trimmed.toUpperCase(), known.toUpperCase());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = known;
            }
        }
        // Tolerance proportionnelle a la longueur : jusqu'a ~30% de caracteres divergents.
        int maxAllowed = Math.max(1, (int) Math.ceil(trimmed.length() * 0.3));
        return (best != null && bestDistance <= maxAllowed) ? best : trimmed;
    }

    /** Departement d'une commune reconnue (apres correction OCR), ou {@code null} si la commune
     * n'a pas ete reconnue dans la bibliotheque de lieux. */
    public static String departementForCommune(String communeCorrected) {
        if (communeCorrected == null) return null;
        for (Map.Entry<String, List<String>> entry : COMMUNES_BY_DEPARTEMENT.entrySet()) {
            if (entry.getValue().contains(communeCorrected)) return entry.getKey();
        }
        return null;
    }

    /** Reconstruit un libelle "Departement X, Commune Y" en recalculant X a partir de Y via la
     * bibliotheque de lieux (fiable) plutot qu'en faisant confiance a la lecture OCR du mot
     * departement (bruitee) — ex: "Uuest" a cote de "Delmas" redevient "Ouest" sans jamais avoir
     * a exposer/saisir un champ departement separe. */
    static String canonicalizeLieu(String deptRaw, String communeRaw) {
        String commune = correctAgainstKnownPlace(communeRaw, COMMUNES);
        String dept = departementForCommune(commune);
        if (dept == null) {
            dept = correctAgainstKnownPlace(deptRaw, DEPARTEMENTS);
        }
        return "Département " + dept + ", Commune " + commune;
    }

    private static List<String> allKnownPlaces() {
        List<String> all = new ArrayList<>(DEPARTEMENTS);
        all.addAll(COMMUNES);
        return all;
    }

    private static int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }

    public static boolean isStopword(String upper) {
        return NAME_STOPWORDS.contains(upper);
    }

    private static final Set<String> NAME_STOPWORDS = Set.of(
            "HAITI", "HAÏTI", "REPIBLIK", "REPUBLIQUE", "CARTE", "IDENTIFICATION", "NATIONALE", "NASYONAL",
            "NATIONALITE", "NASYONALITE", "HARTIEN", "HAÏTIEN", "AYISYEN", "OUEST", "COMMUNE", "PORT",
            "PRINCE", "DEPARTEMENT", "SIGNATURE", "NUMERO", "NIMERO", "RNCS", "CPP", "SYON", "NASY", "NAISSANCE",
            "SEXE", "SEKS", "SIYATI",
            // Bilingue "Prénom / Non" et "Nom / Siyati" : si le libelle deborde dans le recadrage ROI,
            // ces mots ne doivent jamais etre pris pour la valeur elle-meme.
            "NON", "NOM", "PRENOM", "PRÉNOM"
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
                "lieu_naissance", "date_emission", "date_expiration", "nin", "nin_display"
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
        Pattern deptCommune = Pattern.compile(
                "(?i)D[eé]partement\\s+([A-Za-zÉÈÊËÀÂÄÙÛÜÔÖÎÏÇ'\\- ]+?),\\s*Commune\\s+(.+?)(?=\\s+Date|$)");
        Matcher m = deptCommune.matcher(flat);
        if (m.find()) {
            put(fields, "lieu_naissance", canonicalizeLieu(m.group(1), cleanLieu(m.group(2))), 88, threshold);
            return;
        }
        Pattern messyLieu = Pattern.compile(
                "(?i)(?:sr\\s+)?(?:D[eé]partement\\s+)?(Ouest|Artibonite|Centre|Nord|Sud|Nippes|Nord-Est|Nord-Ouest|Sud-Est|Grand'Anse),\\s*Commune\\s+([A-Za-zÉÈÊËÀÂÄ'\\- ]+?)(?=\\s+Date|\\s+°|$)");
        m = messyLieu.matcher(flat);
        if (m.find()) {
            put(fields, "lieu_naissance", canonicalizeLieu(m.group(1), cleanLieu(m.group(2))), 82, threshold);
            return;
        }
        Pattern p = Pattern.compile(
                "(?i)(?:Lieu de Naissance|Kote ou f[eè]t|Naissance\\s*/\\s*Kote)[^\\n]{0,40}\\n?\\s*(.+?)(?=\\s+Date d|$)",
                Pattern.DOTALL);
        m = p.matcher(flat);
        if (m.find()) {
            String lieu = canonicalizeLieuIfDeptCommune(cleanLieu(m.group(1)));
            if (lieu.length() > 5) {
                put(fields, "lieu_naissance", lieu, 85, threshold);
            }
        }
    }

    private String cleanLieu(String raw) {
        return raw.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[°\"]+", "")
                .replaceAll("\\s+Date.*$", "")
                .trim();
    }

    /** Applique {@link #canonicalizeLieu} lorsque le libelle libre reconnu suit deja le format
     * "Departement X, Commune Y" ; sinon renvoie le texte tel quel (repli best-effort). */
    private String canonicalizeLieuIfDeptCommune(String cleaned) {
        Matcher m = Pattern.compile(
                "(?i)D[eé]partement\\s+([A-Za-zÉÈÊËÀÂÄÙÛÜÔÖÎÏÇ'\\- ]+?),\\s*Commune\\s+(.+)$").matcher(cleaned);
        return m.find() ? canonicalizeLieu(m.group(1), m.group(2)) : cleaned;
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

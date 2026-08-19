package ht.oni.cin.application.service;

import ht.oni.cin.domain.model.OcrFieldResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

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
     * Layout reel de la CIN ONI, remesure pixel par pixel le 19/08/2026 sur l'echantillon
     * data/samples/cin-patricia-delaire.png (935x606). Le calibrage precedent (17/08/2026)
     * supposait les deux colonnes parfaitement paralleles ligne pour ligne : en verite les
     * rangees de la colonne gauche (Nom, Lieu de Naissance, Date d'emission) sont decalees
     * plus bas que celles de la colonne droite, et plusieurs boites etaient trop basses en
     * hauteur pour contenir a la fois le libelle bilingue (2 lignes) ET la valeur juste en
     * dessous. Resultat verifie par decoupe reelle : les boites ne capturaient que le libelle
     * et ratifiaient completement la valeur pour nom/sexe/lieu_naissance/date_emission/
     * date_expiration/nin_display -- d'ou "beaucoup de champs mal remplis". Chaque region
     * ci-dessous a ete revalidee en recadrant l'image et en verifiant visuellement que la
     * valeur attendue (ex: "DELAIRE", "F", "0123456789") apparait bien dans le decoupage.
     */
    private static final Region[] REGIONS = {
            new Region("numero_carte", 0.48, 0.03, 0.50, 0.10, 93, 7),
            new Region("prenom", 0.32, 0.16, 0.34, 0.14, 91, 7),
            new Region("sexe", 0.68, 0.19, 0.28, 0.13, 90, 7),
            new Region("nom", 0.32, 0.32, 0.34, 0.12, 91, 7),
            new Region("nationalite", 0.68, 0.30, 0.30, 0.08, 88, 7),
            new Region("lieu_naissance", 0.32, 0.46, 0.64, 0.11, 89, 6),
            new Region("date_naissance", 0.68, 0.42, 0.28, 0.07, 91, 7),
            new Region("date_emission", 0.32, 0.59, 0.34, 0.11, 89, 7),
            new Region("date_expiration", 0.68, 0.58, 0.28, 0.11, 89, 7),
            new Region("nin_display", 0.68, 0.66, 0.30, 0.13, 92, 7),
    };

    private final OcrEngine ocrEngine;
    private final ImageProcessingService imageProcessingService;

    public Map<String, OcrFieldResult> extract(byte[] rawImageBytes, double threshold) {
        Map<String, OcrFieldResult> fields = new HashMap<>();
        try {
            BufferedImage card = imageProcessingService.prepareCardImage(rawImageBytes);
            if (card == null) return fields;
            // Normalise le cadrage (detection de contour + redressement) avant d'appliquer les
            // fractions ROI fixes : sans ca, ce recadrage n'est fiable que sur une photo cadree
            // exactement comme l'echantillon ayant servi a calibrer les REGIONS ci-dessus.
            card = ocrEngine.prepareCard(card);

            for (Region region : REGIONS) {
                try {
                    BufferedImage crop = imageProcessingService.cropRelative(card, region.x, region.y, region.w, region.h);
                    BufferedImage scaled = imageProcessingService.scaleForOcr(crop);
                    String text = ocrEngine.recognize(scaled, region.psm);
                    String value = CinFieldValueParser.clean(region.fieldKey, text);
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
                fields.put("nin", OcrFieldResult.of("nin", CinFieldValueParser.normalizeNin(digits),
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

}

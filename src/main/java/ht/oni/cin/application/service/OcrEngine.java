package ht.oni.cin.application.service;

import ht.oni.cin.config.CinProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;

/**
 * Moteur OCR : service PaddleOCR isolé si configuré (cin.ocr.service-url), avec repli
 * automatique sur Tesseract local si le service distant est indisponible ou en erreur.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class OcrEngine {

    private final CinProperties properties;
    private final PaddleOcrClient paddleOcrClient;

    String recognize(BufferedImage image, int pageSegMode) throws Exception {
        if (paddleOcrClient.isEnabled()) {
            try {
                return paddleOcrClient.recognize(image, pageSegMode);
            } catch (Exception e) {
                if (!properties.getOcr().isLocalFallback()) {
                    throw e;
                }
                log.warn("Service OCR distant indisponible, repli sur Tesseract local: {}", e.getMessage());
            }
        }
        return recognizeLocally(image, pageSegMode);
    }

    /** Détecte le contour de la carte et corrige perspective/rotation vers un cadrage canonique
     * (ocr-service /prepare). Best-effort : sans service distant, il n'existe pas d'équivalent
     * local ; l'image d'entrée est renvoyée telle quelle plutôt que d'échouer l'extraction. */
    BufferedImage prepareCard(BufferedImage image) {
        if (paddleOcrClient.isEnabled()) {
            try {
                return paddleOcrClient.prepareCard(image);
            } catch (Exception e) {
                log.warn("Normalisation de carte indisponible, image d'origine conservee: {}", e.getMessage());
            }
        }
        return image;
    }

    /** OCR pleine carte avec position de chaque ligne, pour l'extraction ancrée sur les libellés
     * ({@link LabelAnchoredOcrService}). Repli sur Tesseract local (lecture ligne par ligne) si
     * le service distant est indisponible ou en erreur, comme pour {@link #recognize}. */
    List<OcrLine> layout(BufferedImage image) throws Exception {
        if (paddleOcrClient.isEnabled()) {
            try {
                return paddleOcrClient.layout(image);
            } catch (Exception e) {
                if (!properties.getOcr().isLocalFallback()) {
                    throw e;
                }
                log.warn("Service OCR distant indisponible pour le layout, repli sur Tesseract local: {}", e.getMessage());
            }
        }
        return layoutLocally(image);
    }

    private List<OcrLine> layoutLocally(BufferedImage image) throws Exception {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(resolveTessdataPath());
        tesseract.setLanguage(resolveLanguage());
        tesseract.setOcrEngineMode(1);
        int w = image.getWidth();
        int h = image.getHeight();
        List<Word> words = tesseract.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE);
        return words.stream()
                .filter(word -> word.getText() != null && !word.getText().isBlank())
                .map(word -> {
                    var box = word.getBoundingBox();
                    return new OcrLine(
                            word.getText().trim(),
                            word.getConfidence(),
                            box.x / (double) w,
                            box.y / (double) h,
                            (box.x + box.width) / (double) w,
                            (box.y + box.height) / (double) h);
                })
                .toList();
    }

    private String recognizeLocally(BufferedImage image, int pageSegMode) throws Exception {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(resolveTessdataPath());
        tesseract.setLanguage(resolveLanguage());
        tesseract.setPageSegMode(pageSegMode);
        tesseract.setOcrEngineMode(1);
        return tesseract.doOCR(image);
    }

    private String resolveLanguage() {
        String lang = properties.getOcr().getLanguage();
        if (lang == null || lang.isBlank()) return "fra";
        if (lang.contains("hat") && !Path.of(resolveTessdataPath(), "hat.traineddata").toFile().exists()) {
            return "fra";
        }
        return lang;
    }

    private String resolveTessdataPath() {
        if (!properties.getOcr().getTesseractDataPath().isBlank()) {
            return properties.getOcr().getTesseractDataPath();
        }
        Path local = Path.of("data", "tessdata");
        if (local.toFile().exists()) {
            return local.toAbsolutePath().toString();
        }
        return "./data/tessdata";
    }
}

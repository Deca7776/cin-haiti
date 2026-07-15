package ht.oni.cin.application.service;

import ht.oni.cin.config.CinProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

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

package ht.oni.cin.application.service;

import ht.oni.cin.config.CinProperties;
import lombok.RequiredArgsConstructor;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor
class TesseractRunner {

    private final CinProperties properties;

    String recognize(BufferedImage image, int pageSegMode) throws TesseractException {
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

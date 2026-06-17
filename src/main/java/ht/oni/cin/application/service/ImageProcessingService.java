package ht.oni.cin.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * Prétraitement image CIN haïtienne : photo à GAUCHE, texte à droite (format ONI).
 */
@Service
@Slf4j
public class ImageProcessingService {

    private static final int MIN_WIDTH = 1800;
    private static final int PHOTO_SIZE = 400;

    public BufferedImage prepareCardImage(byte[] input) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(input));
        if (image == null) return null;
        return enhanceContrast(resizeIfNeeded(image));
    }

    public BufferedImage cropRelative(BufferedImage image, double rx, double ry, double rw, double rh) {
        int x = Math.max(0, (int) (image.getWidth() * rx));
        int y = Math.max(0, (int) (image.getHeight() * ry));
        int w = Math.min((int) (image.getWidth() * rw), image.getWidth() - x);
        int h = Math.min((int) (image.getHeight() * rh), image.getHeight() - y);
        w = Math.max(1, w);
        h = Math.max(1, h);
        return image.getSubimage(x, y, w, h);
    }

    public BufferedImage scaleForOcr(BufferedImage src) {
        int targetW = Math.max(MIN_WIDTH / 2, src.getWidth() * 2);
        int targetH = (int) ((double) src.getHeight() / src.getWidth() * targetW);
        return scale(src, targetW, Math.max(targetH, 40));
    }

    public byte[] preprocess(byte[] input) throws Exception {
        BufferedImage enhanced = prepareCardImage(input);
        if (enhanced == null) {
            throw new IllegalArgumentException("Format d'image non supporté");
        }
        BufferedImage textRegion = cropTextRegion(enhanced);
        BufferedImage ocrReady = scale(textRegion, Math.max(MIN_WIDTH, textRegion.getWidth()), textRegion.getHeight());
        return toJpegBytes(ocrReady, 0.92f);
    }

    /** Carte entière redimensionnée — utile pour numéro de carte et en-tête. */
    public byte[] preprocessFull(byte[] input) throws Exception {
        BufferedImage enhanced = prepareCardImage(input);
        if (enhanced == null) {
            throw new IllegalArgumentException("Format d'image non supporté");
        }
        BufferedImage ocrReady = scale(enhanced, Math.max(MIN_WIDTH, enhanced.getWidth()), enhanced.getHeight());
        return toJpegBytes(ocrReady, 0.92f);
    }

    /** Zone texte : 35 % à 100 % de la largeur (photo CIN haïtienne à gauche). */
    private BufferedImage cropTextRegion(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int x = (int) (w * 0.32);
        int cropW = w - x;
        return image.getSubimage(x, 0, cropW, h);
    }

    public String extractPhotoBase64(byte[] input) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(input));
            if (image == null) return null;

            int w = image.getWidth();
            int h = image.getHeight();
            // Photo CIN haïtienne : zone GAUCHE
            int photoX = (int) (w * 0.04);
            int photoY = (int) (h * 0.22);
            int photoW = (int) (w * 0.28);
            int photoH = (int) (h * 0.62);

            BufferedImage cropped = image.getSubimage(
                    Math.min(photoX, w - 1),
                    Math.min(photoY, h - 1),
                    Math.min(photoW, w - photoX),
                    Math.min(photoH, h - photoY)
            );

            BufferedImage square = cropToSquare(cropped);
            BufferedImage scaled = scale(square, PHOTO_SIZE, PHOTO_SIZE);
            byte[] jpeg = toJpegBytes(scaled, 0.90f);
            return Base64.getEncoder().encodeToString(jpeg);
        } catch (Exception e) {
            log.warn("Extraction photo échouée: {}", e.getMessage());
            return null;
        }
    }

    private BufferedImage resizeIfNeeded(BufferedImage image) {
        if (image.getWidth() >= MIN_WIDTH) return image;
        double scale = (double) MIN_WIDTH / image.getWidth();
        int newH = (int) (image.getHeight() * scale);
        return scale(image, MIN_WIDTH, newH);
    }

    private BufferedImage enhanceContrast(BufferedImage image) {
        BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();

        for (int y = 0; y < result.getHeight(); y++) {
            for (int x = 0; x < result.getWidth(); x++) {
                int rgb = result.getRGB(x, y);
                int r = clamp((int) ((((rgb >> 16) & 0xFF) - 128) * 1.2 + 128));
                int gr = clamp((int) ((((rgb >> 8) & 0xFF) - 128) * 1.2 + 128));
                int b = clamp((int) (((rgb & 0xFF) - 128) * 1.2 + 128));
                result.setRGB(x, y, (r << 16) | (gr << 8) | b);
            }
        }
        return result;
    }

    private BufferedImage cropToSquare(BufferedImage image) {
        int size = Math.min(image.getWidth(), image.getHeight());
        int x = (image.getWidth() - size) / 2;
        int y = (image.getHeight() - size) / 2;
        return image.getSubimage(x, y, size, size);
    }

    private BufferedImage scale(BufferedImage src, int w, int h) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }

    private byte[] toJpegBytes(BufferedImage image, float quality) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        var writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            ImageIO.write(image, "jpeg", baos);
            return baos.toByteArray();
        }
        var writer = writers.next();
        var ios = ImageIO.createImageOutputStream(baos);
        writer.setOutput(ios);
        var param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
        }
        writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
        writer.dispose();
        ios.close();
        return baos.toByteArray();
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}

package ht.oni.cin.application.service;

import ht.oni.cin.config.CinProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

/** Client HTTP vers le service OCR isolé (PaddleOCR, cf. architecture — service OCR isolé). */
@Component
@RequiredArgsConstructor
class PaddleOcrClient {

    private record OcrResponse(String text, double confidence) {}

    private record LineDto(String text, double confidence, double x0, double y0, double x1, double y1) {}

    private record LayoutResponse(List<LineDto> lines) {}

    private final CinProperties properties;
    private volatile RestClient restClient;

    boolean isEnabled() {
        String url = properties.getOcr().getServiceUrl();
        return url != null && !url.isBlank();
    }

    String recognize(BufferedImage image, int pageSegMode) throws Exception {
        MultiValueMap<String, Object> body = multipartImage(image);
        body.add("psm", String.valueOf(pageSegMode));

        OcrResponse response = client().post()
                .uri("/recognize")
                .body(body)
                .retrieve()
                .body(OcrResponse.class);
        return response != null && response.text() != null ? response.text() : "";
    }

    /** Détecte le contour de la carte dans la photo et corrige perspective/rotation vers un
     * cadrage canonique — voir ocr-service /prepare. Renvoie l'image telle quelle si le service
     * n'a rien pu redresser (best-effort). */
    BufferedImage prepareCard(BufferedImage image) throws Exception {
        byte[] jpeg = client().post()
                .uri("/prepare")
                .body(multipartImage(image))
                .retrieve()
                .body(byte[].class);
        if (jpeg == null || jpeg.length == 0) return image;
        BufferedImage warped = ImageIO.read(new ByteArrayInputStream(jpeg));
        return warped != null ? warped : image;
    }

    /** OCR pleine carte en un seul appel, avec la position de chaque ligne — voir ocr-service
     * /layout. Utilisé par {@link LabelAnchoredOcrService} pour localiser les champs par leur
     * libellé bilingue plutôt que par coordonnées fixes. */
    List<OcrLine> layout(BufferedImage image) throws Exception {
        LayoutResponse response = client().post()
                .uri("/layout")
                .body(multipartImage(image))
                .retrieve()
                .body(LayoutResponse.class);
        if (response == null || response.lines() == null) return List.of();
        return response.lines().stream()
                .map(l -> new OcrLine(l.text(), l.confidence(), l.x0(), l.y0(), l.x1(), l.y1()))
                .toList();
    }

    private MultiValueMap<String, Object> multipartImage(BufferedImage image) throws Exception {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(png.toByteArray()) {
            @Override
            public String getFilename() {
                return "crop.png";
            }
        });
        return body;
    }

    private RestClient client() {
        RestClient c = restClient;
        if (c == null) {
            synchronized (this) {
                c = restClient;
                if (c == null) {
                    int timeout = properties.getOcr().getServiceTimeoutMs();
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(timeout);
                    factory.setReadTimeout(timeout);
                    c = RestClient.builder()
                            .baseUrl(properties.getOcr().getServiceUrl())
                            .requestFactory(factory)
                            .build();
                    restClient = c;
                }
            }
        }
        return c;
    }
}

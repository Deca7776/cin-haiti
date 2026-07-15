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
import java.io.ByteArrayOutputStream;

/** Client HTTP vers le service OCR isolé (PaddleOCR, cf. architecture — service OCR isolé). */
@Component
@RequiredArgsConstructor
class PaddleOcrClient {

    private record OcrResponse(String text, double confidence) {}

    private final CinProperties properties;
    private volatile RestClient restClient;

    boolean isEnabled() {
        String url = properties.getOcr().getServiceUrl();
        return url != null && !url.isBlank();
    }

    String recognize(BufferedImage image, int pageSegMode) throws Exception {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(png.toByteArray()) {
            @Override
            public String getFilename() {
                return "crop.png";
            }
        });
        body.add("psm", String.valueOf(pageSegMode));

        OcrResponse response = client().post()
                .uri("/recognize")
                .body(body)
                .retrieve()
                .body(OcrResponse.class);
        return response != null && response.text() != null ? response.text() : "";
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

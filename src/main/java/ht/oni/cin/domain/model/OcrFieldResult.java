package ht.oni.cin.domain.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
public class OcrFieldResult {
    private String fieldName;
    private String value;
    private double confidence;
    private boolean needsReview;

    public static OcrFieldResult of(String fieldName, String value, double confidence, double threshold) {
        return OcrFieldResult.builder()
                .fieldName(fieldName)
                .value(value)
                .confidence(confidence)
                .needsReview(confidence < threshold)
                .build();
    }
}

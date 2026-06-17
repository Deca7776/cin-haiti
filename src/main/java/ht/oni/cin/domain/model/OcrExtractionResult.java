package ht.oni.cin.domain.model;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class OcrExtractionResult {
    private Map<String, OcrFieldResult> fields;
    private double averageConfidence;
    private String rawText;
    private boolean success;
    private String errorMessage;
}

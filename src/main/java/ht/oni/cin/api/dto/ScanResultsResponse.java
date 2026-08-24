package ht.oni.cin.api.dto;

import ht.oni.cin.application.service.SessionService;
import ht.oni.cin.domain.model.OcrExtractionResult;
import ht.oni.cin.domain.model.OcrFieldResult;
import ht.oni.cin.domain.model.SessionStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@Builder
public class ScanResultsResponse {
    private UUID sessionId;
    private SessionStatus status;
    private String idempotencyToken;
    private double averageConfidence;
    private boolean ocrSuccess;
    private String errorMessage;
    private int fieldsExtracted;
    private int fieldsExpected;
    private List<FieldDto> fields;
    private String photoBase64;

    @Data
    @Builder
    public static class FieldDto {
        private String name;
        private String value;
        private double confidence;
        private boolean needsReview;
    }

    public static ScanResultsResponse from(SessionService.SessionData session,
                                           OcrExtractionResult ocr, String photoBase64) {
        List<FieldDto> fields = ocr != null && ocr.getFields() != null
                ? ocr.getFields().entrySet().stream()
                .map(e -> toField(e.getValue()))
                .collect(Collectors.toList())
                : List.of();

        int extracted = countExtractedFields(ocr);
        return ScanResultsResponse.builder()
                .sessionId(session.getSessionId())
                .status(session.getStatus())
                .idempotencyToken(session.getIdempotencyToken())
                .averageConfidence(ocr != null ? ocr.getAverageConfidence() : 0)
                .ocrSuccess(ocr != null && ocr.isSuccess())
                .errorMessage(ocr != null ? ocr.getErrorMessage() : null)
                .fieldsExtracted(extracted)
                .fieldsExpected(10)
                .fields(fields)
                .photoBase64(photoBase64)
                .build();
    }

    private static int countExtractedFields(OcrExtractionResult ocr) {
        if (ocr == null || ocr.getFields() == null) return 0;
        String[] keys = {"numero_carte", "prenom", "nom", "sexe", "nationalite", "date_naissance",
                "lieu_naissance", "date_emission", "date_expiration", "nin_display"};
        int n = 0;
        for (String k : keys) {
            if (ocr.getFields().containsKey(k)) n++;
        }
        return n;
    }

    private static FieldDto toField(OcrFieldResult f) {
        return FieldDto.builder()
                .name(f.getFieldName())
                .value(f.getValue())
                .confidence(f.getConfidence())
                .needsReview(f.isNeedsReview())
                .build();
    }
}

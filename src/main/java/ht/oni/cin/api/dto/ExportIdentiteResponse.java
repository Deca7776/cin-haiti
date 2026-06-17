package ht.oni.cin.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class ExportIdentiteResponse {
    private String nin;
    private Map<String, Object> data;
    private Instant exportedAt;
}

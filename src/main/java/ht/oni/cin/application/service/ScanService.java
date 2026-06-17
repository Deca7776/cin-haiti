package ht.oni.cin.application.service;

import ht.oni.cin.api.dto.ScanResultsResponse;
import ht.oni.cin.api.dto.SessionResponse;
import ht.oni.cin.api.dto.ValidateIdentiteRequest;
import ht.oni.cin.domain.model.AuditEventType;
import ht.oni.cin.domain.model.OcrExtractionResult;
import ht.oni.cin.domain.model.SessionStatus;
import ht.oni.cin.infrastructure.persistence.entity.CinIdentiteEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScanService {

    private final SessionService sessionService;
    private final ImageProcessingService imageProcessingService;
    private final OcrService ocrService;
    private final IdentiteService identiteService;
    private final AuditService auditService;

    public SessionResponse openSession(String operatorId, String ipAddress) {
        SessionService.SessionData session = sessionService.openSession(operatorId, ipAddress);
        return SessionResponse.from(session);
    }

    public ScanResultsResponse processImage(UUID sessionId, String operatorId, MultipartFile file, String ipAddress)
            throws Exception {
        sessionService.verifySessionAccess(sessionId, operatorId);
        byte[] raw = file.getBytes();
        sessionService.storeEncryptedImage(sessionId, raw);

        OcrExtractionResult ocr = ocrService.extract(raw);
        String photoBase64 = imageProcessingService.extractPhotoBase64(raw);

        sessionService.storeOcrResults(sessionId, ocr, photoBase64);

        auditService.log(AuditEventType.OCR_COMPLETED, operatorId, "OPERATOR",
                null, sessionId, null, null,
                Map.of("success", ocr.isSuccess(), "avgConfidence", ocr.getAverageConfidence()), ipAddress);

        SessionService.SessionData session = sessionService.getSession(sessionId);
        return ScanResultsResponse.from(session, ocr, photoBase64);
    }

    public ScanResultsResponse getResults(UUID sessionId, String operatorId) {
        sessionService.verifySessionAccess(sessionId, operatorId);
        SessionService.SessionData session = sessionService.getSession(sessionId);
        return ScanResultsResponse.from(session, session.getOcrResult(), null);
    }

    public CinIdentiteEntity validateAndSave(UUID sessionId, String operatorId,
                                              ValidateIdentiteRequest request, String ipAddress) {
        sessionService.verifySessionAccess(sessionId, operatorId);
        SessionService.SessionData session = sessionService.getSession(sessionId);

        if (!session.getIdempotencyToken().equals(request.getIdempotencyToken())) {
            throw new IdempotencyException("Jeton d'idempotence invalide — soumission déjà traitée ou expirée");
        }

        CinIdentiteEntity saved = identiteService.saveValidated(request, session, operatorId, ipAddress);
        sessionService.closeSession(sessionId, SessionStatus.COMPLETED, operatorId, ipAddress);
        return saved;
    }

    public void cancelSession(UUID sessionId, String operatorId, String ipAddress) {
        sessionService.verifySessionAccess(sessionId, operatorId);
        sessionService.closeSession(sessionId, SessionStatus.CANCELLED, operatorId, ipAddress);
    }

    public static class IdempotencyException extends RuntimeException {
        public IdempotencyException(String message) { super(message); }
    }
}

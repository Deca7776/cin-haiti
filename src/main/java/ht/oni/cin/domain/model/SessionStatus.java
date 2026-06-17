package ht.oni.cin.domain.model;

public enum SessionStatus {
    OPEN,
    PROCESSING,
    AWAITING_VALIDATION,
    COMPLETED,
    CANCELLED,
    EXPIRED,
    ERROR
}

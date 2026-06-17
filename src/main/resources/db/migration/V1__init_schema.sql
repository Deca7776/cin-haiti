-- Table principale CIN (EF-08)
CREATE TABLE cin_identite (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nin             VARCHAR(13) NOT NULL UNIQUE,
    nom             VARCHAR(100) NOT NULL,
    prenom          VARCHAR(200) NOT NULL,
    date_naissance  DATE NOT NULL,
    lieu_naissance  VARCHAR(200) NOT NULL,
    sexe            CHAR(1) NOT NULL CHECK (sexe IN ('M', 'F')),
    adresse         TEXT,
    departement     VARCHAR(50),
    date_emission   DATE NOT NULL,
    date_expiration DATE NOT NULL,
    photo_ref       VARCHAR(500),
    score_ocr_moyen DECIMAL(5,2),
    validee_par     VARCHAR(100) NOT NULL,
    validee_le      TIMESTAMPTZ NOT NULL,
    session_id      UUID NOT NULL,
    cree_le         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    modifie_le      TIMESTAMPTZ,
    statut          VARCHAR(20) NOT NULL DEFAULT 'ACTIF' CHECK (statut IN ('ACTIF', 'ARCHIVE', 'SUSPENDU')),
    checksum        VARCHAR(64) NOT NULL,
    consentement_documente BOOLEAN NOT NULL DEFAULT FALSE,
    base_legale     VARCHAR(100)
);

CREATE INDEX idx_cin_identite_nin ON cin_identite(nin);
CREATE INDEX idx_cin_identite_statut ON cin_identite(statut);

-- Table d'audit append-only (EF-09)
CREATE TABLE cin_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    event_type      VARCHAR(50) NOT NULL,
    actor_id        VARCHAR(100),
    actor_type      VARCHAR(30) NOT NULL,
    nin             VARCHAR(13),
    session_id      UUID,
    target_system   VARCHAR(100),
    payload_before  JSONB,
    payload_after   JSONB,
    ip_address      VARCHAR(45),
    signature       VARCHAR(128) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_nin ON cin_audit_log(nin);
CREATE INDEX idx_audit_session ON cin_audit_log(session_id);
CREATE INDEX idx_audit_created ON cin_audit_log(created_at);

-- Clés API systèmes tiers (EF-10)
CREATE TABLE cin_api_client (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    client_name     VARCHAR(100) NOT NULL UNIQUE,
    api_key_hash    VARCHAR(128) NOT NULL,
    allowed_fields  VARCHAR(500) NOT NULL,
    can_access_photo BOOLEAN NOT NULL DEFAULT FALSE,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ NOT NULL,
    last_rotated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Consentements titulaires
CREATE TABLE cin_consentement (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nin             VARCHAR(13) NOT NULL,
    client_id       UUID NOT NULL REFERENCES cin_api_client(id),
    base_legale     VARCHAR(100) NOT NULL,
    consentement_le TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expire_le       TIMESTAMPTZ,
    actif           BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_consentement_nin_client ON cin_consentement(nin, client_id);

-- Registre des instances (réponse CDC [2])
CREATE TABLE cin_instance_registry (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_id     VARCHAR(100) NOT NULL UNIQUE,
    hostname        VARCHAR(200) NOT NULL,
    registered_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_heartbeat  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    active          BOOLEAN NOT NULL DEFAULT TRUE
);

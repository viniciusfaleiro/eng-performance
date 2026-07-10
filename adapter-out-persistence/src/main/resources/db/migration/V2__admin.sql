-- S1 (admin completo) · contas de usuário + configuração da plataforma

CREATE TABLE user_account (
    id            VARCHAR(128) PRIMARY KEY,
    name          VARCHAR(200) NOT NULL,
    email         VARCHAR(320) NOT NULL UNIQUE,
    role          VARCHAR(32)  NOT NULL,
    status        VARCHAR(32)  NOT NULL,
    person_id     VARCHAR(128),
    password_hash VARCHAR(200) NOT NULL
);

-- Singleton configuration rows (id = 'default').
CREATE TABLE ado_integration (
    id                    VARCHAR(16) PRIMARY KEY,
    organization_url      VARCHAR(500),
    pat_secret            VARCHAR(500),
    production_stage_rule VARCHAR(500),
    connected             BOOLEAN NOT NULL DEFAULT FALSE,
    last_validated_at     TIMESTAMPTZ
);

CREATE TABLE ai_convention (
    id             VARCHAR(16) PRIMARY KEY,
    strategy       VARCHAR(16) NOT NULL,
    trailer        VARCHAR(500),
    tag            VARCHAR(64),
    regex          VARCHAR(500),
    case_sensitive BOOLEAN NOT NULL DEFAULT FALSE
);

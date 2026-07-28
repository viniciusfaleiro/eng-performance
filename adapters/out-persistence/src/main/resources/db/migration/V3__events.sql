-- S3 (motor de métricas) · eventos crus de atividade (uma tabela polimórfica).
-- O adapter real do ADO (S9) preenche as mesmas colunas — a "casa" já existe aqui.

CREATE TABLE raw_event (
    id                 VARCHAR(128) PRIMARY KEY,
    type               VARCHAR(16)  NOT NULL,
    occurred_at        TIMESTAMPTZ  NOT NULL,
    repo_key           VARCHAR(200),
    committer_identity VARCHAR(320),
    numeric_value      DOUBLE PRECISION,
    phase              VARCHAR(64),
    ai                 BOOLEAN      NOT NULL DEFAULT FALSE,
    detail             JSONB        NOT NULL DEFAULT '{}'
);

CREATE INDEX idx_raw_event_type_time ON raw_event (type, occurred_at);
CREATE INDEX idx_raw_event_repo       ON raw_event (repo_key);
CREATE INDEX idx_raw_event_identity   ON raw_event (committer_identity);

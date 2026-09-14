-- Reset de senha por token + configuração do servidor de e-mail (SMTP), singleton como
-- ado_integration/ai_convention.

CREATE TABLE password_reset_token (
    id          VARCHAR(64)  PRIMARY KEY,
    account_id  VARCHAR(128) NOT NULL,
    token_hash  VARCHAR(128) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    consumed_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX password_reset_token_hash_idx ON password_reset_token (token_hash);
CREATE INDEX password_reset_token_account_idx ON password_reset_token (account_id, created_at);

CREATE TABLE smtp_settings (
    id                  VARCHAR(16) PRIMARY KEY,
    enabled             BOOLEAN NOT NULL DEFAULT FALSE,
    host                VARCHAR(255),
    port                INTEGER,
    transport           VARCHAR(16) NOT NULL DEFAULT 'NONE',
    username            VARCHAR(255),
    password_ciphertext TEXT,
    from_address        VARCHAR(320),
    from_name           VARCHAR(200),
    app_base_url        VARCHAR(500)
);

INSERT INTO smtp_settings (id, enabled, transport) VALUES ('default', FALSE, 'NONE');

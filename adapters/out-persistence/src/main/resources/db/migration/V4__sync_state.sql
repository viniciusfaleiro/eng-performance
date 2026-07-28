-- S9 (adapter ADO real) · cursor da sincronização: high-water mark + resumo da última carga.
-- Uma linha única (id fixo); o loader avança o watermark e grava o resumo a cada sync.

CREATE TABLE sync_state (
    id             VARCHAR(32)  PRIMARY KEY,
    watermark      TIMESTAMPTZ,
    last_synced_at TIMESTAMPTZ,
    event_count    BIGINT       NOT NULL DEFAULT 0
);

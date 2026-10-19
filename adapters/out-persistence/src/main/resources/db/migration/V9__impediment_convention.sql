-- Convenção que marca um work item como impedido: o campo do ADO (reference name) e o prefixo da
-- tag que carrega o motivo. Linha única, id = 'default'. Ambos nulos = sinal desligado, que é um
-- estado válido: um board sem sinal de impedimento não deve ter suas fases reinterpretadas por um
-- palpite.
CREATE TABLE impediment_convention (
    id         VARCHAR(16) PRIMARY KEY,
    field_name VARCHAR(200),
    tag_prefix VARCHAR(64)
);

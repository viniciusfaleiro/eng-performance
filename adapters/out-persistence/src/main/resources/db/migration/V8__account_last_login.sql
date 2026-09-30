-- Quando cada conta acessou pela última vez. Anulável e sem backfill de propósito: o dado não
-- existe retroativamente, e NULL significa "nunca acessou" — que numa implantação é a informação
-- mais útil, não a ausência dela.
ALTER TABLE user_account ADD COLUMN last_login_at TIMESTAMPTZ;

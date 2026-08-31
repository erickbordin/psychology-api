ALTER TABLE consulta ADD COLUMN id_serie UUID;

CREATE INDEX idx_consulta_serie ON consulta (id_serie);

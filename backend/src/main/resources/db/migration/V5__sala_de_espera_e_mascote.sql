-- Sala de espera do Match: a sala nasce esperando a galera e só vira deck quando o dono começa.
-- Cada sala ganha um mascote sorteado (índice da lista de imagens em public/mascot/rooms).

ALTER TABLE match_room
    ADD COLUMN mascot     SMALLINT    NOT NULL DEFAULT 0 CHECK (mascot >= 0),
    ADD COLUMN started_at TIMESTAMPTZ;

-- as salas que já existiam começavam na hora em que eram criadas
UPDATE match_room SET started_at = created_at;

-- lista de salas esperando gente (a tela do Match consulta de poucos em poucos segundos)
CREATE INDEX ix_match_room_waiting ON match_room (created_at) WHERE started_at IS NULL;

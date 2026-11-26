-- Deck da sala do Match, calculado uma vez quando o dono começa: ordem pelo gosto do grupo
-- ("média sem miséria" dos percentis de cada participante). Todos votam nessa mesma ordem, e o
-- desempate de dois matches ao mesmo tempo também segue ela.
-- Salas que começaram antes desta migration não têm linhas aqui e continuam no md5(seed:id).
CREATE TABLE match_room_deck (
    room_id     BIGINT           NOT NULL REFERENCES match_room (id) ON DELETE CASCADE,
    title_id    BIGINT           NOT NULL REFERENCES title (id) ON DELETE CASCADE,
    position    INTEGER          NOT NULL,
    group_score DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (room_id, title_id)
);
CREATE UNIQUE INDEX ux_match_room_deck_position ON match_room_deck (room_id, position);

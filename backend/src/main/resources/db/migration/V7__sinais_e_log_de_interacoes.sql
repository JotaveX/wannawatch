-- Sinais de interação persistidos (passa, talvez, já vi, salvar) e o log de tudo o que o usuário faz.

-- Estado atual de cada sinal. Um título pode ter mais de um (ex.: SEEN + "quero ver" em title_like).
-- O "quero ver" continua em title_like.
CREATE TABLE user_title_signal (
    user_id    BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    title_id   BIGINT      NOT NULL REFERENCES title (id) ON DELETE CASCADE,
    kind       VARCHAR(10) NOT NULL CHECK (kind IN ('PASS', 'MAYBE', 'SEEN', 'SAVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, title_id, kind)
);
CREATE INDEX ix_user_title_signal_title ON user_title_signal (title_id);

-- Log append-only: nunca é apagado nem atualizado. Não tem FK para title de propósito
-- (guarda external_id + media_type), para o histórico sobreviver a uma reimportação do catálogo.
-- room_id também fica sem FK: a sala é apagada quando esvazia, o histórico não.
CREATE TABLE interaction_event (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    title_external_id INTEGER     NOT NULL,
    media_type        VARCHAR(10) NOT NULL CHECK (media_type IN ('MOVIE', 'SERIES')),
    title_id          BIGINT      NOT NULL,
    kind              VARCHAR(20) NOT NULL CHECK (kind IN ('LIKE', 'UNLIKE', 'PASS', 'MAYBE', 'SEEN', 'SAVED',
                                                           'SIGNAL_REMOVED', 'MATCH_VOTE_YES', 'MATCH_VOTE_NO',
                                                           'MATCH_UNVOTE')),
    -- SIGNAL_REMOVED: qual sinal saiu (PASS, MAYBE, SEEN ou SAVED); nos outros fica nulo
    value             VARCHAR(20),
    source            VARCHAR(10) CHECK (source IN ('SWIPE', 'REELS', 'DETAILS', 'HOME', 'MATCH')),
    room_id           BIGINT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_interaction_event_user ON interaction_event (user_id, created_at);

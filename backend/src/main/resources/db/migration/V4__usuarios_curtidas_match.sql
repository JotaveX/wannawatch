-- Usuários, login, curtidas e salas de Match.
-- Não há cadastro: as contas são criadas aqui. Por enquanto todo mundo é "amigo" de todo mundo.

-- pgcrypto: hash bcrypt das senhas (crypt/gen_salt), feito e conferido no próprio banco.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE app_user (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(40)  NOT NULL UNIQUE,
    display_name  VARCHAR(80)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

INSERT INTO app_user (username, display_name, password_hash) VALUES
    ('jotave', 'Jotave', crypt('wannawatch', gen_salt('bf', 10))),
    ('bia',    'Bia',    crypt('wannawatch', gen_salt('bf', 10))),
    ('caio',   'Caio',   crypt('wannawatch', gen_salt('bf', 10)));

-- Sessões de login. O token só existe no cliente; aqui fica o SHA-256 dele (hex).
CREATE TABLE auth_session (
    token_hash CHAR(64)    PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_auth_session_user ON auth_session (user_id);

-- "Quero ver" do Swipe e do Reels.
CREATE TABLE title_like (
    user_id    BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    title_id   BIGINT      NOT NULL REFERENCES title (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, title_id)
);
CREATE INDEX ix_title_like_title ON title_like (title_id);

-- Sala de Match: todos votam no mesmo deck (mesma seed e filtros) até um título
-- receber "quero ver" de todos os participantes.
CREATE TABLE match_room (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code            VARCHAR(8)   NOT NULL UNIQUE,
    owner_id        BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    media_type      VARCHAR(10)  CHECK (media_type IN ('MOVIE', 'SERIES')),
    genre_slug      VARCHAR(120),
    seed            VARCHAR(64)  NOT NULL,
    winner_title_id BIGINT       REFERENCES title (id) ON DELETE SET NULL,
    matched_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE match_participant (
    room_id   BIGINT      NOT NULL REFERENCES match_room (id) ON DELETE CASCADE,
    user_id   BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (room_id, user_id)
);
CREATE INDEX ix_match_participant_user ON match_participant (user_id);

CREATE TABLE match_vote (
    room_id    BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    title_id   BIGINT      NOT NULL REFERENCES title (id) ON DELETE CASCADE,
    liked      BOOLEAN     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (room_id, user_id, title_id),
    FOREIGN KEY (room_id, user_id) REFERENCES match_participant (room_id, user_id) ON DELETE CASCADE
);
CREATE INDEX ix_match_vote_liked ON match_vote (room_id, title_id) WHERE liked;

-- Catálogo: títulos (filmes e séries), gêneros, pessoas, créditos e rankings.

-- Gêneros: uma linha por id da TMDB. Ids que aparecem nas listas de filme e de série
-- (ex.: 18 Drama) viram uma linha só, com as duas flags ligadas.
CREATE TABLE genre (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id INTEGER      NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(120) NOT NULL UNIQUE,
    for_movie   BOOLEAN      NOT NULL DEFAULT FALSE,
    for_series  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Títulos: tabela única para filme e série; colunas específicas de cada tipo são opcionais.
-- Dados só de exibição (produtoras, países, emissoras, onde assistir) ficam em "details".
CREATE TABLE title (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id         INTEGER          NOT NULL,
    media_type          VARCHAR(10)      NOT NULL CHECK (media_type IN ('MOVIE', 'SERIES')),
    title               VARCHAR(500)     NOT NULL,
    original_title      VARCHAR(500),
    tagline             VARCHAR(500),
    overview            TEXT,
    original_language   VARCHAR(10),
    release_date        DATE,
    runtime_minutes     INTEGER,
    seasons_count       INTEGER,
    episodes_count      INTEGER,
    status              VARCHAR(50),
    poster_path         VARCHAR(255),
    backdrop_path       VARCHAR(255),
    vote_average        DOUBLE PRECISION,
    vote_count          INTEGER,
    popularity          DOUBLE PRECISION NOT NULL DEFAULT 0,
    age_rating          VARCHAR(10),
    budget              BIGINT,
    revenue             BIGINT,
    trailer_youtube_key VARCHAR(50),
    details             JSONB            NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ      NOT NULL DEFAULT now(),
    last_synced_at      TIMESTAMPTZ,
    CONSTRAINT uq_title_external UNIQUE (external_id, media_type)
);

-- Home/Reels/Swipe ordenam por popularidade (com id como desempate, usado no cursor).
CREATE INDEX ix_title_popularity ON title (popularity DESC, id DESC);
CREATE INDEX ix_title_type_popularity ON title (media_type, popularity DESC, id DESC);

CREATE TABLE title_genre (
    title_id BIGINT NOT NULL REFERENCES title (id) ON DELETE CASCADE,
    genre_id BIGINT NOT NULL REFERENCES genre (id) ON DELETE CASCADE,
    PRIMARY KEY (title_id, genre_id)
);
CREATE INDEX ix_title_genre_genre ON title_genre (genre_id, title_id);

-- Pessoas (elenco e equipe).
CREATE TABLE person (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id          INTEGER      NOT NULL UNIQUE,
    name                 VARCHAR(300) NOT NULL,
    profile_path         VARCHAR(255),
    known_for_department VARCHAR(100),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE title_credit (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title_id     BIGINT      NOT NULL REFERENCES title (id) ON DELETE CASCADE,
    person_id    BIGINT      NOT NULL REFERENCES person (id) ON DELETE CASCADE,
    credit_type  VARCHAR(10) NOT NULL CHECK (credit_type IN ('CAST', 'CREW')),
    character    VARCHAR(500),
    job          VARCHAR(100),
    department   VARCHAR(100),
    credit_order INTEGER     NOT NULL DEFAULT 0
);
CREATE INDEX ix_title_credit_title ON title_credit (title_id, credit_type, credit_order);
CREATE INDEX ix_title_credit_person ON title_credit (person_id);

-- Rankings vindos das listas da TMDB (ex.: trending do dia → "Top 10 de hoje").
CREATE TABLE title_ranking (
    list_key  VARCHAR(50) NOT NULL,
    position  INTEGER     NOT NULL,
    title_id  BIGINT      NOT NULL REFERENCES title (id) ON DELETE CASCADE,
    synced_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (list_key, position)
);
CREATE INDEX ix_title_ranking_title ON title_ranking (title_id);

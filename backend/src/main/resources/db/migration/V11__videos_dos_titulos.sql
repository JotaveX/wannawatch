-- Vídeos curtos dos títulos, para o Reels. Guardamos só metadados: o vídeo continua no YouTube e
-- toca pelo player oficial (embed). Um título pode ter vários vídeos (fallback quando um sai do ar).
CREATE TABLE title_video (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title_id          BIGINT       NOT NULL REFERENCES title (id) ON DELETE CASCADE,
    source            VARCHAR(20)  NOT NULL CHECK (source IN ('TMDB', 'YOUTUBE_SEARCH', 'MANUAL')),
    platform          VARCHAR(20)  NOT NULL CHECK (platform IN ('YOUTUBE')),
    external_key      VARCHAR(50)  NOT NULL,
    video_title       VARCHAR(500),
    channel_title     VARCHAR(255),
    published_at      TIMESTAMPTZ,
    duration_seconds  INTEGER,
    -- proporção do player (o YouTube devolve as dimensões sem tarjas): altura > largura = vertical
    width             INTEGER,
    height            INTEGER,
    embeddable        BOOLEAN      NOT NULL DEFAULT TRUE,
    blocked_in_region BOOLEAN      NOT NULL DEFAULT FALSE,
    search_rank       INTEGER,     -- posição no resultado da busca (0 = primeiro)
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                      CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    checked_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_title_video UNIQUE (title_id, platform, external_key)
);
CREATE INDEX ix_title_video_title_status ON title_video (title_id, status);

-- Uma linha por título já buscado no YouTube, mesmo sem resultado: a busca custa cota,
-- então não repetimos sem pedir.
CREATE TABLE title_video_search (
    title_id      BIGINT       PRIMARY KEY REFERENCES title (id) ON DELETE CASCADE,
    query         VARCHAR(600) NOT NULL,
    results_count INTEGER      NOT NULL,
    searched_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

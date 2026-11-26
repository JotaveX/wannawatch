-- Keywords da TMDB (até 20 por título) e os títulos que a TMDB recomenda para cada um.
-- Os dois alimentam o motor de recomendação (vetor de conteúdo e um bônus de similaridade).

-- id = id da keyword na TMDB (é estável e não colide com nada nosso).
CREATE TABLE keyword (
    id   INTEGER      PRIMARY KEY,
    name VARCHAR(200) NOT NULL
);

CREATE TABLE title_keyword (
    title_id   BIGINT  NOT NULL REFERENCES title (id) ON DELETE CASCADE,
    keyword_id INTEGER NOT NULL REFERENCES keyword (id) ON DELETE CASCADE,
    PRIMARY KEY (title_id, keyword_id)
);
CREATE INDEX ix_title_keyword_keyword ON title_keyword (keyword_id, title_id);

-- Recomendações da TMDB para cada título, na ordem dela. Guarda o id da TMDB (e não o nosso)
-- porque muitos recomendados não estão no catálogo; quando os dois estão, vira um sinal fraco.
CREATE TABLE title_tmdb_recommendation (
    title_id    BIGINT      NOT NULL REFERENCES title (id) ON DELETE CASCADE,
    position    SMALLINT    NOT NULL,
    external_id INTEGER     NOT NULL,
    media_type  VARCHAR(10) NOT NULL CHECK (media_type IN ('MOVIE', 'SERIES')),
    PRIMARY KEY (title_id, position)
);

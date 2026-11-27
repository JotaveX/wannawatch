-- Catálogo maior para a busca: títulos vindos do export diário da TMDB ou de uma busca que não
-- achou nada aqui ficam com in_feeds = false. Eles aparecem na busca, nos detalhes e nas listas
-- de Filmes/Séries, mas não na Home, no Swipe, no Reels, no Match nem na recomendação.
-- Os das listas importadas todo dia (populares, em alta...) continuam em todos os lugares.
ALTER TABLE title ADD COLUMN in_feeds BOOLEAN NOT NULL DEFAULT TRUE;
CREATE INDEX ix_title_in_feeds ON title (id) WHERE in_feeds;

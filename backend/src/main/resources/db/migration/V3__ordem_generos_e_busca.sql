-- Ordem dos gêneros de cada título (a TMDB manda o principal primeiro).
ALTER TABLE title_genre ADD COLUMN position SMALLINT NOT NULL DEFAULT 0;

-- unaccent() não é IMMUTABLE, então não pode ser usada em índice. Este wrapper fixa o dicionário.
CREATE OR REPLACE FUNCTION f_unaccent(text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
AS $$ SELECT public.unaccent('public.unaccent'::regdictionary, $1) $$;

-- Busca fuzzy (pg_trgm) sem acento e sem diferenciar maiúsculas.
CREATE INDEX ix_title_title_trgm ON title USING gin (f_unaccent(lower(title)) gin_trgm_ops);
CREATE INDEX ix_title_original_title_trgm ON title USING gin (f_unaccent(lower(original_title)) gin_trgm_ops);
CREATE INDEX ix_person_name_trgm ON person USING gin (f_unaccent(lower(name)) gin_trgm_ops);

-- Perfil: e-mail (só leitura por enquanto), cor da esfera, avatar do Bu (o fantasminha),
-- gênero e título favoritos.
--
-- color: índice da paleta do front (core/social/user-colors.ts). Os 5 primeiros são as cores
--        que cada conta já tinha (eram fixas por id), então ninguém muda de cor ao migrar.
-- mascot: índice das imagens do Bu (public/mascot/rooms); NULL = esfera com as iniciais.
-- favorite_*: sem FK de propósito. O catálogo é reimportado/truncado (TRUNCATE ... CASCADE
--             apagaria os usuários); id que sumiu simplesmente volta como null no LEFT JOIN.

ALTER TABLE app_user
    ADD COLUMN email             VARCHAR(254) UNIQUE,
    ADD COLUMN color             SMALLINT NOT NULL DEFAULT 0 CHECK (color BETWEEN 0 AND 7),
    ADD COLUMN mascot            SMALLINT CHECK (mascot BETWEEN 0 AND 7),
    ADD COLUMN favorite_genre_id BIGINT,
    ADD COLUMN favorite_title_id BIGINT;

UPDATE app_user SET color = (id - 1) % 5, email = username || '@exemplo.com';

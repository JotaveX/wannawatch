-- Cadastro em etapas: criar a conta, escolher pelo menos 5 títulos (e um favorito) e confirmar o e-mail.
--
-- email_verified_at: quando o e-mail foi confirmado; NULL = ainda não confirmou.
-- titles_picked_at:  quando terminou a etapa de escolher os títulos; NULL = ainda não escolheu.
-- As contas de teste da V4 já entram com as duas etapas feitas.

ALTER TABLE app_user
    ADD COLUMN email_verified_at TIMESTAMPTZ,
    ADD COLUMN titles_picked_at  TIMESTAMPTZ;

UPDATE app_user SET email_verified_at = now(), titles_picked_at = now();

-- O e-mail é guardado sempre em minúsculas, mas garante que "Bia@x.com" e "bia@x.com" não coexistam.
CREATE UNIQUE INDEX ux_app_user_email_lower ON app_user (lower(email));

-- Código de 6 dígitos para confirmar o e-mail. Um por usuário; pedir outro substitui o anterior.
-- Como o token de sessão, no banco fica só o SHA-256 dele.
CREATE TABLE email_verification (
    user_id    BIGINT      PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    code_hash  CHAR(64)    NOT NULL,
    attempts   SMALLINT    NOT NULL DEFAULT 0,
    sent_at    TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);

-- Nova origem de interação: os títulos escolhidos no cadastro.
ALTER TABLE interaction_event DROP CONSTRAINT interaction_event_source_check;
ALTER TABLE interaction_event ADD CONSTRAINT interaction_event_source_check
    CHECK (source IN ('SWIPE', 'REELS', 'DETAILS', 'HOME', 'MATCH', 'ONBOARDING'));

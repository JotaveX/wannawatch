-- Foto de perfil enviada pelo usuário (guardada no Cloudflare R2; em desenvolvimento, em disco).
--
-- photo_key: chave do objeto no bucket, para apagar a foto antiga quando trocar ou remover.
-- photo_url: endereço público da foto, como o front usa no <img>. Guardado pronto para que
--            qualquer SELECT de usuário (UserRef) já traga a foto sem depender de configuração.
-- Sem foto (NULL), o avatar volta a ser o Bu escolhido ou as iniciais.

ALTER TABLE app_user
    ADD COLUMN photo_key VARCHAR(200),
    ADD COLUMN photo_url VARCHAR(500);

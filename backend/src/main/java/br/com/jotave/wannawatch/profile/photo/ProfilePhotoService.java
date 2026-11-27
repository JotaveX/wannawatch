package br.com.jotave.wannawatch.profile.photo;

import br.com.jotave.wannawatch.auth.AuthUser;
import br.com.jotave.wannawatch.profile.ProfileService;
import br.com.jotave.wannawatch.profile.ProfileService.Profile;
import br.com.jotave.wannawatch.shared.web.BadRequestException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Envia e remove a foto de perfil. Sem {@code @Transactional} de propósito: o upload para o R2 não
 * deve segurar uma transação aberta, e cada UPDATE abaixo já é atômico sozinho.
 *
 * <p>Cada envio ganha uma chave nova ({@code avatars/{id}/{uuid}.jpg}), então a URL muda e nenhum
 * cache mostra a foto velha. A anterior é apagada depois que o banco já aponta para a nova.
 */
@Service
public class ProfilePhotoService {

    private static final Logger log = LoggerFactory.getLogger(ProfilePhotoService.class);

    private final PhotoStorage storage;
    private final ProfileService profiles;
    private final JdbcClient jdbc;

    public ProfilePhotoService(PhotoStorage storage, ProfileService profiles, JdbcClient jdbc) {
        this.storage = storage;
        this.profiles = profiles;
        this.jdbc = jdbc;
    }

    public Profile upload(AuthUser me, byte[] original) {
        if (original == null || original.length == 0) throw new BadRequestException("Escolha uma imagem.");
        byte[] jpeg = PhotoImages.normalize(original);
        String key = "avatars/%d/%s.jpg".formatted(me.id(), UUID.randomUUID());
        String url = storage.put(key, jpeg);
        String previous;
        try {
            previous = swap(me.id(), key, url);
        } catch (RuntimeException e) {
            deleteQuietly(key);
            throw e;
        }
        if (previous != null) deleteQuietly(previous);
        return profiles.get(me);
    }

    public Profile remove(AuthUser me) {
        String previous = swap(me.id(), null, null);
        if (previous != null) deleteQuietly(previous);
        return profiles.get(me);
    }

    /** Troca a foto e devolve a chave da anterior (null se não tinha), travando a linha para dois envios ao mesmo tempo não perderem uma chave. */
    private String swap(long userId, String key, String url) {
        List<String> previous = jdbc.sql("""
                UPDATE app_user u SET photo_key = :key, photo_url = :url
                FROM (SELECT id, photo_key FROM app_user WHERE id = :id FOR UPDATE) old
                WHERE u.id = old.id
                RETURNING old.photo_key""")
                .param("key", key).param("url", url).param("id", userId)
                .query((rs, n) -> rs.getString("photo_key"))
                .list();
        return previous.isEmpty() ? null : previous.getFirst();
    }

    /** Foto antiga que sobrar no bucket não quebra nada: só registra e segue. */
    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Não deu pra apagar a foto antiga {}", key, e);
        }
    }
}

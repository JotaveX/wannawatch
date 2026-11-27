package br.com.jotave.wannawatch.profile.photo;

/**
 * Onde as fotos de perfil ficam. Em produção é o Cloudflare R2 ({@link R2PhotoStorage}); sem as
 * credenciais do R2, cai para o disco local ({@link LocalPhotoStorage}), servido pelo próprio back.
 */
public interface PhotoStorage {

    /**
     * Guarda a foto (JPEG) na chave dada e devolve o endereço público dela.
     *
     * @throws PhotoStorageException se o armazenamento recusar ou não responder
     */
    String put(String key, byte[] jpeg);

    /** Apaga a foto. Não falha se ela já não existir. */
    void delete(String key);
}

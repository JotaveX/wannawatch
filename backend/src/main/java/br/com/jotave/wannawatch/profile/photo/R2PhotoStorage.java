package br.com.jotave.wannawatch.profile.photo;

import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Fotos no Cloudflare R2, pela API compatível com S3. O bucket precisa estar com acesso público
 * (subdomínio r2.dev ou domínio próprio), que é o {@code publicUrl}: o front carrega a foto
 * direto de lá, sem passar pelo back.
 *
 * <p>Configuração do cliente segundo a doc do R2: região {@code auto} e chunked encoding desligado
 * (com ele ligado o R2 responde 403 por assinatura diferente).
 */
class R2PhotoStorage implements PhotoStorage, AutoCloseable {

    /** a chave muda a cada envio, então o arquivo de uma chave nunca muda: pode ficar em cache para sempre */
    private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final S3Client s3;
    private final String bucket;
    private final String publicUrl;

    R2PhotoStorage(String accountId, String accessKeyId, String secretAccessKey, String bucket, String publicUrl) {
        this(S3Client.builder()
                .endpointOverride(URI.create("https://%s.r2.cloudflarestorage.com".formatted(accountId)))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .region(Region.of("auto"))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build())
                .build(), bucket, publicUrl);
    }

    R2PhotoStorage(S3Client s3, String bucket, String publicUrl) {
        this.s3 = s3;
        this.bucket = bucket;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    @Override
    public String put(String key, byte[] jpeg) {
        try {
            s3.putObject(b -> b.bucket(bucket).key(key).contentType("image/jpeg").cacheControl(CACHE_CONTROL),
                    RequestBody.fromBytes(jpeg));
        } catch (SdkException e) {
            throw new PhotoStorageException("Falha ao enviar a foto " + key + " para o R2", e);
        }
        return publicUrl + "/" + key;
    }

    @Override
    public void delete(String key) {
        try {
            s3.deleteObject(b -> b.bucket(bucket).key(key));
        } catch (SdkException e) {
            throw new PhotoStorageException("Falha ao apagar a foto " + key + " do R2", e);
        }
    }

    @Override
    public void close() {
        s3.close();
    }
}

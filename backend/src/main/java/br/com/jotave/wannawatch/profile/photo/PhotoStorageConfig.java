package br.com.jotave.wannawatch.profile.photo;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Com {@code R2_ACCESS_KEY_ID} preenchida as fotos vão para o Cloudflare R2; sem ela, para a pasta
 * {@code wannawatch.photos.local.dir}, servida pelo back em {@code /fotos/**}.
 */
@Configuration
class PhotoStorageConfig {

    private static final Logger log = LoggerFactory.getLogger(PhotoStorageConfig.class);

    @Bean
    PhotoStorage photoStorage(
            @Value("${wannawatch.photos.r2.account-id:}") String accountId,
            @Value("${wannawatch.photos.r2.access-key-id:}") String accessKeyId,
            @Value("${wannawatch.photos.r2.secret-access-key:}") String secretAccessKey,
            @Value("${wannawatch.photos.r2.bucket:}") String bucket,
            @Value("${wannawatch.photos.r2.public-url:}") String publicUrl,
            @Value("${wannawatch.photos.local.dir}") Path localDir,
            @Value("${wannawatch.photos.local.public-url}") String localPublicUrl) {
        if (!accessKeyId.isBlank()) {
            require(accountId, "R2_ACCOUNT_ID");
            require(secretAccessKey, "R2_SECRET_ACCESS_KEY");
            require(bucket, "R2_BUCKET");
            require(publicUrl, "R2_PUBLIC_URL");
            log.info("Fotos de perfil no Cloudflare R2 (bucket {})", bucket);
            return new R2PhotoStorage(accountId, accessKeyId, secretAccessKey, bucket, publicUrl);
        }
        LocalPhotoStorage local = new LocalPhotoStorage(localDir, localPublicUrl);
        log.info("Fotos de perfil em disco, em {} (preencha R2_* no .env para usar o R2)", local.dir());
        return local;
    }

    /** Só no modo em disco: serve a pasta das fotos, já que não há bucket público. */
    @Bean
    WebMvcConfigurer localPhotoFiles(PhotoStorage storage) {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                if (storage instanceof LocalPhotoStorage local) {
                    registry.addResourceHandler("/fotos/**")
                            .addResourceLocations(local.dir().toUri().toString());
                }
            }
        };
    }

    private static void require(String value, String env) {
        if (value.isBlank()) {
            throw new IllegalStateException("Com R2_ACCESS_KEY_ID preenchida, " + env + " também é obrigatória.");
        }
    }
}

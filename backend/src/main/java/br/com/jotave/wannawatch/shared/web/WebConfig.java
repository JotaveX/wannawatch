package br.com.jotave.wannawatch.shared.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebConfig(@Value("${wannawatch.cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    /** A API pública é só leitura. O endpoint de admin não é chamado pelo navegador. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "HEAD", "OPTIONS");
    }

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("wannawatch? API")
                .version("v1")
                .description("Catálogo de filmes e séries (dados da TMDB) para a Home, o Swipe e o Reels."));
    }
}

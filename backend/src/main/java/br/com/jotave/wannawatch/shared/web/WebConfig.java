package br.com.jotave.wannawatch.shared.web;

import br.com.jotave.wannawatch.auth.AuthUserResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;
    private final AuthUserResolver authUserResolver;

    public WebConfig(@Value("${wannawatch.cors.allowed-origins}") String[] allowedOrigins,
                     AuthUserResolver authUserResolver) {
        this.allowedOrigins = allowedOrigins;
        this.authUserResolver = authUserResolver;
    }

    /** O login vai no header Authorization (sem cookie), então não precisa de allowCredentials. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                // X-Admin-Token: página /dev/videos chama os endpoints de admin pelo navegador
                .allowedHeaders("Authorization", "Content-Type", "X-Admin-Token");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(authUserResolver);
    }

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("wannawatch? API")
                .version("v1")
                .description("Catálogo de filmes e séries (dados da TMDB), curtidas e salas de Match."))
                .components(new Components().addSecuritySchemes("token",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")))
                .addSecurityItem(new SecurityRequirement().addList("token"));
    }
}

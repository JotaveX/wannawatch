package br.com.jotave.wannawatch.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.ParameterizedType;
import java.util.Optional;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Preenche parâmetros {@link AuthUser} a partir do header {@code Authorization: Bearer <token>}.
 *
 * <p>{@code AuthUser} exige login (401 sem sessão válida). {@code Optional<AuthUser>} deixa o login
 * opcional: sem header vem vazio, mas um token inválido ou vencido continua dando 401, para o front
 * saber que a sessão caiu em vez de receber em silêncio a versão sem personalização.
 */
@Component
public class AuthUserResolver implements HandlerMethodArgumentResolver {

    private final AuthService auth;

    public AuthUserResolver(AuthService auth) {
        this.auth = auth;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == AuthUser.class || isOptionalUser(parameter);
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
                                  NativeWebRequest request, WebDataBinderFactory binders) {
        String token = bearerToken(request.getNativeRequest(HttpServletRequest.class));
        if (token == null && isOptionalUser(parameter)) return Optional.empty();
        AuthUser user = auth.userByToken(token)
                .orElseThrow(() -> new UnauthorizedException("Faça login para continuar."));
        return isOptionalUser(parameter) ? Optional.of(user) : user;
    }

    private static boolean isOptionalUser(MethodParameter parameter) {
        return parameter.getParameterType() == Optional.class
                && parameter.getGenericParameterType() instanceof ParameterizedType p
                && p.getActualTypeArguments()[0] == AuthUser.class;
    }

    static String bearerToken(HttpServletRequest request) {
        if (request == null) return null;
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        return header.substring(7).trim();
    }
}

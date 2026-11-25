package br.com.jotave.wannawatch.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Preenche parâmetros {@link AuthUser} a partir do header {@code Authorization: Bearer <token>}. */
@Component
public class AuthUserResolver implements HandlerMethodArgumentResolver {

    private final AuthService auth;

    public AuthUserResolver(AuthService auth) {
        this.auth = auth;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == AuthUser.class;
    }

    @Override
    public AuthUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
                                    NativeWebRequest request, WebDataBinderFactory binders) {
        String token = bearerToken(request.getNativeRequest(HttpServletRequest.class));
        return auth.userByToken(token)
                .orElseThrow(() -> new UnauthorizedException("Faça login para continuar."));
    }

    static String bearerToken(HttpServletRequest request) {
        if (request == null) return null;
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        return header.substring(7).trim();
    }
}

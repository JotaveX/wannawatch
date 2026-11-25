package br.com.jotave.wannawatch.auth;

/**
 * Usuário logado. Basta declarar um parâmetro deste tipo num endpoint para ele exigir login:
 * o {@link AuthUserResolver} lê o token do header e responde 401 se não houver sessão válida.
 */
public record AuthUser(long id, String username, String displayName, UserRef ref) {

    static AuthUser of(UserRef ref) {
        return new AuthUser(ref.id(), ref.username(), ref.displayName(), ref);
    }
}

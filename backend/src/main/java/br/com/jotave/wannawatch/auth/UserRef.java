package br.com.jotave.wannawatch.auth;

/**
 * Como um usuário aparece nos contratos da API (quem curtiu, participantes da sala...).
 *
 * @param color  índice da paleta de cores do front (0–7)
 * @param mascot índice da imagem do Bu escolhida como avatar; null = iniciais
 */
public record UserRef(long id, String username, String displayName, int color, Integer mascot) {

    /** Colunas para um SELECT com alias {@code u} de app_user, na ordem que {@link #from} lê. */
    public static final String COLUMNS = "u.id, u.username, u.display_name, u.color, u.mascot";

    public static UserRef from(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new UserRef(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"),
                rs.getInt("color"), (Integer) rs.getObject("mascot", Integer.class));
    }
}

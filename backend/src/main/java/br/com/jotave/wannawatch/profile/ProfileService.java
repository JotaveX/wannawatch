package br.com.jotave.wannawatch.profile;

import br.com.jotave.wannawatch.auth.AuthUser;
import br.com.jotave.wannawatch.auth.UserRef;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.GenreRef;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.TitleCard;
import br.com.jotave.wannawatch.catalog.application.TitleCards;
import br.com.jotave.wannawatch.shared.web.BadRequestException;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** O perfil de quem está logado: ver e editar. */
@Service
@Transactional
public class ProfileService {

    /** tamanho da paleta de cores do front (frontend/src/app/core/social/user-colors.ts) */
    static final int COLORS = 8;
    /** quantas imagens do Bu o front tem (frontend/src/app/shared/ui/mascots.ts) */
    static final int MASCOTS = 8;

    private final JdbcClient jdbc;
    private final TitleCards cards;

    public ProfileService(JdbcClient jdbc, TitleCards cards) {
        this.jdbc = jdbc;
        this.cards = cards;
    }

    public record Stats(int likes, int matches) {}

    public record Profile(UserRef user, String email, GenreRef favoriteGenre, TitleCard favoriteTitle, Stats stats) {}

    public record Changes(String displayName, int color, Integer mascot, Long favoriteGenreId, Long favoriteTitleId) {}

    private record Row(UserRef user, String email, Long genreId, String genreName, String genreSlug, Long titleId) {}

    @Transactional(readOnly = true)
    public Profile get(AuthUser me) {
        Row row = jdbc.sql("""
                SELECT %s, u.email, g.id AS genre_id, g.name AS genre_name, g.slug AS genre_slug, t.id AS title_id
                FROM app_user u
                LEFT JOIN genre g ON g.id = u.favorite_genre_id
                LEFT JOIN title t ON t.id = u.favorite_title_id
                WHERE u.id = :id""".formatted(UserRef.COLUMNS))
                .param("id", me.id())
                .query((rs, n) -> new Row(UserRef.from(rs), rs.getString("email"),
                        rs.getObject("genre_id", Long.class), rs.getString("genre_name"), rs.getString("genre_slug"),
                        rs.getObject("title_id", Long.class)))
                .single();
        GenreRef genre = row.genreId() == null ? null : new GenreRef(row.genreId(), row.genreName(), row.genreSlug());
        TitleCard title = row.titleId() == null ? null
                : cards.load(List.of(row.titleId())).stream().findFirst().orElse(null);
        return new Profile(row.user(), row.email(), genre, title, stats(me.id()));
    }

    public Profile update(AuthUser me, Changes changes) {
        String name = changes.displayName() == null ? "" : changes.displayName().strip().replaceAll("\\s+", " ");
        if (name.isEmpty()) throw new BadRequestException("O nome não pode ficar vazio.");
        if (changes.color() < 0 || changes.color() >= COLORS) throw new BadRequestException("Cor inválida.");
        Integer mascot = changes.mascot();
        if (mascot != null && (mascot < 0 || mascot >= MASCOTS)) throw new BadRequestException("Bu inválido.");
        if (changes.favoriteGenreId() != null && !exists("genre", changes.favoriteGenreId()))
            throw new BadRequestException("Gênero não encontrado.");
        if (changes.favoriteTitleId() != null && !exists("title", changes.favoriteTitleId()))
            throw new BadRequestException("Título não encontrado.");

        jdbc.sql("""
                UPDATE app_user SET display_name = :name, color = :color, mascot = :mascot,
                       favorite_genre_id = :genre, favorite_title_id = :title
                WHERE id = :id""")
                .param("name", name).param("color", changes.color()).param("mascot", mascot)
                .param("genre", changes.favoriteGenreId()).param("title", changes.favoriteTitleId())
                .param("id", me.id())
                .update();
        return get(me);
    }

    private Stats stats(long userId) {
        return jdbc.sql("""
                SELECT (SELECT count(*) FROM title_like WHERE user_id = :u) AS likes,
                       (SELECT count(*) FROM match_participant p JOIN match_room r ON r.id = p.room_id
                         WHERE p.user_id = :u AND r.winner_title_id IS NOT NULL) AS matches""")
                .param("u", userId)
                .query((rs, n) -> new Stats(rs.getInt("likes"), rs.getInt("matches")))
                .single();
    }

    private boolean exists(String table, long id) {
        // table vem só das duas constantes acima, nunca da requisição
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM " + table + " WHERE id = :id)")
                .param("id", id).query(Boolean.class).single();
    }
}

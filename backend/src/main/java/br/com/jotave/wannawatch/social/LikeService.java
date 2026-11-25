package br.com.jotave.wannawatch.social;

import br.com.jotave.wannawatch.auth.AuthUser;
import br.com.jotave.wannawatch.auth.UserRef;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.TitleCard;
import br.com.jotave.wannawatch.catalog.application.TitleCards;
import br.com.jotave.wannawatch.shared.web.NotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Quero ver" do Swipe e do Reels. Ainda não existe lista de amigos: os "amigos" de alguém
 * são todos os outros usuários.
 */
@Service
@Transactional
public class LikeService {

    private final JdbcClient jdbc;
    private final TitleCards cards;

    public LikeService(JdbcClient jdbc, TitleCards cards) {
        this.jdbc = jdbc;
        this.cards = cards;
    }

    public record TitleLikes(long titleId, boolean likedByMe, List<UserRef> friends) {}

    public record FriendsLiked(TitleCard title, boolean likedByMe, List<UserRef> friends) {}

    public void like(AuthUser user, long titleId) {
        boolean exists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM title WHERE id = :id)")
                .param("id", titleId).query(Boolean.class).single();
        if (!exists) throw new NotFoundException("Título " + titleId + " não encontrado.");
        jdbc.sql("INSERT INTO title_like (user_id, title_id) VALUES (:u, :t) ON CONFLICT DO NOTHING")
                .param("u", user.id()).param("t", titleId).update();
    }

    public void unlike(AuthUser user, long titleId) {
        jdbc.sql("DELETE FROM title_like WHERE user_id = :u AND title_id = :t")
                .param("u", user.id()).param("t", titleId).update();
    }

    /** Uma entrada por id pedido, na mesma ordem, mesmo sem curtidas. */
    @Transactional(readOnly = true)
    public List<TitleLikes> forTitles(AuthUser user, List<Long> titleIds) {
        Set<Long> ids = new LinkedHashSet<>(titleIds);
        if (ids.isEmpty()) return List.of();
        Set<Long> mine = new HashSet<>();
        Map<Long, List<UserRef>> friends = new HashMap<>();
        jdbc.sql("""
                SELECT l.title_id, %s
                FROM title_like l JOIN app_user u ON u.id = l.user_id
                WHERE l.title_id IN (:ids)
                ORDER BY l.created_at DESC, u.id""".formatted(UserRef.COLUMNS))
                .param("ids", ids)
                .query((rs, n) -> {
                    long titleId = rs.getLong("title_id");
                    long userId = rs.getLong("id");
                    if (userId == user.id()) mine.add(titleId);
                    else friends.computeIfAbsent(titleId, k -> new ArrayList<>())
                            .add(UserRef.from(rs));
                    return null;
                })
                .list();
        return ids.stream()
                .map(id -> new TitleLikes(id, mine.contains(id), friends.getOrDefault(id, List.of())))
                .toList();
    }

    /** Títulos que os outros curtiram: primeiro os curtidos por mais gente, depois os mais recentes. */
    @Transactional(readOnly = true)
    public List<FriendsLiked> friendsLiked(AuthUser user, int size) {
        List<Long> ids = jdbc.sql("""
                SELECT title_id FROM title_like
                WHERE user_id <> :me
                GROUP BY title_id
                ORDER BY count(*) DESC, max(created_at) DESC, title_id
                LIMIT :size""")
                .param("me", user.id()).param("size", size)
                .query(Long.class).list();
        Map<Long, TitleLikes> likes = new LinkedHashMap<>();
        for (TitleLikes l : forTitles(user, ids)) likes.put(l.titleId(), l);
        return cards.load(ids).stream()
                .map(card -> {
                    TitleLikes l = likes.get(card.id());
                    return new FriendsLiked(card, l != null && l.likedByMe(), l == null ? List.of() : l.friends());
                })
                .toList();
    }
}

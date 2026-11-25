package br.com.jotave.wannawatch.match;

import br.com.jotave.wannawatch.auth.AuthUser;
import br.com.jotave.wannawatch.auth.UserRef;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.GenreRef;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.TitleCard;
import br.com.jotave.wannawatch.catalog.application.SqlFilters;
import br.com.jotave.wannawatch.catalog.application.TitleCards;
import br.com.jotave.wannawatch.catalog.domain.MediaType;
import br.com.jotave.wannawatch.match.MatchDtos.Participant;
import br.com.jotave.wannawatch.match.MatchDtos.RoomResponse;
import br.com.jotave.wannawatch.match.MatchDtos.RoomStatus;
import br.com.jotave.wannawatch.shared.web.BadRequestException;
import br.com.jotave.wannawatch.shared.web.ConflictException;
import br.com.jotave.wannawatch.shared.web.ForbiddenException;
import br.com.jotave.wannawatch.shared.web.Cursor;
import br.com.jotave.wannawatch.shared.web.CursorResponse;
import br.com.jotave.wannawatch.shared.web.NotFoundException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Salas de Match: todo mundo vota no mesmo deck (ordem aleatória fixa pela seed da sala, com os
 * filtros escolhidos por quem criou). A sala acaba no primeiro título que receber "quero ver" de
 * todos os participantes (mínimo de 2).
 *
 * Ciclo: a sala nasce esperando (aparece na lista para todo mundo e qualquer um entra), o dono
 * começa quando tiver pelo menos 2 pessoas e daí em diante ninguém novo entra.
 *
 * Toda escrita trava a linha da sala (SELECT ... FOR UPDATE): dois votos simultâneos no mesmo
 * título não podem deixar o match passar batido.
 */
@Service
@Transactional
public class MatchService {

    static final int MIN_PARTICIPANTS = 2;
    static final int MAX_ROOMS_LISTED = 50;
    /** quantas imagens de mascote o front tem (frontend/src/app/shared/ui/mascots.ts) */
    static final int MASCOTS = 8;
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;

    private final JdbcClient jdbc;
    private final TitleCards cards;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public MatchService(JdbcClient jdbc, TitleCards cards, Clock clock) {
        this.jdbc = jdbc;
        this.cards = cards;
        this.clock = clock;
    }

    private record Room(long id, String code, long ownerId, MediaType mediaType, String genreSlug, String seed,
                        int mascot, Long winnerId, Instant startedAt, Instant matchedAt, Instant createdAt) {

        boolean started() {
            return startedAt != null;
        }

        Room withWinner(long titleId, Instant at) {
            return new Room(id, code, ownerId, mediaType, genreSlug, seed, mascot, titleId, startedAt, at, createdAt);
        }
    }

    // ------------------------------------------------------------------ comandos

    public RoomResponse create(AuthUser user, MediaType mediaType, String genreSlug) {
        String genre = genreSlug == null || genreSlug.isBlank() ? null : genreSlug.trim().toLowerCase();
        if (genre != null && genre(genre) == null) throw new BadRequestException("Gênero desconhecido: " + genre + ".");
        String seed = randomString("abcdefghijklmnopqrstuvwxyz0123456789", 12);
        int mascot = random.nextInt(MASCOTS);
        for (int attempt = 0; attempt < 5; attempt++) {
            Optional<Long> id = jdbc.sql("""
                    INSERT INTO match_room (code, owner_id, media_type, genre_slug, seed, mascot)
                    VALUES (:code, :owner, :type, :genre, :seed, :mascot)
                    ON CONFLICT (code) DO NOTHING RETURNING id""")
                    .param("code", randomString(CODE_ALPHABET, CODE_LENGTH)).param("owner", user.id())
                    .param("type", mediaType == null ? null : mediaType.name()).param("genre", genre)
                    .param("seed", seed).param("mascot", mascot)
                    .query(Long.class).optional();
            if (id.isPresent()) {
                addParticipant(id.get(), user.id());
                return view(lockedRoomById(id.get()));
            }
        }
        throw new IllegalStateException("Não foi possível gerar um código de sala livre.");
    }

    /**
     * Entrar é idempotente para quem já está na sala. Gente nova só entra enquanto a sala espera:
     * depois que o dono começa, o deck é só de quem estava lá.
     */
    public RoomResponse join(AuthUser user, String code) {
        Room room = lockedRoom(code);
        if (!isParticipant(room.id(), user.id())) {
            if (room.started()) throw new ConflictException("A sala " + room.code() + " já começou.");
            addParticipant(room.id(), user.id());
        }
        return view(room);
    }

    /** O dono começa a votação (precisa de pelo menos 2 pessoas). Começar de novo não muda nada. */
    public RoomResponse start(AuthUser user, String code) {
        Room room = lockedParticipantRoom(user, code);
        if (room.started()) return view(room);
        if (room.ownerId() != user.id()) throw new ForbiddenException("Só quem criou a sala pode começar.");
        if (participantCount(room.id()) < MIN_PARTICIPANTS) {
            throw new ConflictException("Chame pelo menos mais uma pessoa antes de começar.");
        }
        Instant now = clock.instant();
        jdbc.sql("UPDATE match_room SET started_at = :at WHERE id = :r")
                .param("at", Timestamp.from(now)).param("r", room.id()).update();
        return view(lockedRoomById(room.id()));
    }

    /** O dono fecha a sala enquanto ela espera (o X na lista). Quem estava nela recebe 404 depois. */
    public void close(AuthUser user, String code) {
        Room room = lockedParticipantRoom(user, code);
        if (room.ownerId() != user.id()) throw new ForbiddenException("Só quem criou a sala pode fechar.");
        if (room.started()) throw new ConflictException("A sala já começou; quem quiser sair usa \"Sair da sala\".");
        jdbc.sql("DELETE FROM match_room WHERE id = :r").param("r", room.id()).update();
    }

    public RoomResponse vote(AuthUser user, String code, long titleId, boolean liked) {
        Room room = lockedParticipantRoom(user, code);
        requireStarted(room);
        if (room.winnerId() != null) return view(room);
        boolean titleExists = jdbc.sql("SELECT EXISTS (SELECT 1 FROM title WHERE id = :id)")
                .param("id", titleId).query(Boolean.class).single();
        if (!titleExists) throw new NotFoundException("Título " + titleId + " não encontrado.");
        jdbc.sql("""
                INSERT INTO match_vote (room_id, user_id, title_id, liked) VALUES (:r, :u, :t, :l)
                ON CONFLICT (room_id, user_id, title_id) DO UPDATE SET liked = EXCLUDED.liked, created_at = now()""")
                .param("r", room.id()).param("u", user.id()).param("t", titleId).param("l", liked)
                .update();
        return view(liked ? settle(room) : room);
    }

    /** Desfaz um voto (o "voltar" do swipe). Depois do match não muda mais nada. */
    public RoomResponse unvote(AuthUser user, String code, long titleId) {
        Room room = lockedParticipantRoom(user, code);
        requireStarted(room);
        if (room.winnerId() == null) {
            jdbc.sql("DELETE FROM match_vote WHERE room_id = :r AND user_id = :u AND title_id = :t")
                    .param("r", room.id()).param("u", user.id()).param("t", titleId).update();
        }
        return view(room);
    }

    /**
     * Sai da sala e apaga os votos. Quem fica pode passar a concordar em algum título (o que saiu era
     * o único que faltava), então confere o match de novo. Sala vazia é apagada.
     */
    public void leave(AuthUser user, String code) {
        Room room = lockedParticipantRoom(user, code);
        jdbc.sql("DELETE FROM match_participant WHERE room_id = :r AND user_id = :u")
                .param("r", room.id()).param("u", user.id()).update();
        Optional<Long> nextOwner = jdbc.sql("""
                SELECT user_id FROM match_participant WHERE room_id = :r ORDER BY joined_at, user_id LIMIT 1""")
                .param("r", room.id()).query(Long.class).optional();
        if (nextOwner.isEmpty()) {
            jdbc.sql("DELETE FROM match_room WHERE id = :r").param("r", room.id()).update();
            return;
        }
        if (room.ownerId() == user.id()) {
            jdbc.sql("UPDATE match_room SET owner_id = :o WHERE id = :r")
                    .param("o", nextOwner.get()).param("r", room.id()).update();
        }
        if (room.started() && room.winnerId() == null) settle(room);
    }

    // ------------------------------------------------------------------ consultas

    @Transactional(readOnly = true)
    public RoomResponse room(AuthUser user, String code) {
        return view(participantRoom(user, code, false));
    }

    /**
     * Salas abertas: as que esperam gente (de qualquer pessoa) e as que eu estou votando, para poder
     * voltar a elas. Salas com match não aparecem. Mais novas primeiro.
     */
    @Transactional(readOnly = true)
    public List<RoomResponse> openRooms(AuthUser user) {
        return jdbc.sql(ROOM_SELECT + """
                 WHERE r.winner_title_id IS NULL
                   AND (r.started_at IS NULL
                        OR EXISTS (SELECT 1 FROM match_participant p WHERE p.room_id = r.id AND p.user_id = :u))
                ORDER BY r.created_at DESC, r.id DESC LIMIT :limit""")
                .param("u", user.id()).param("limit", MAX_ROOMS_LISTED)
                .query(ROOM_ROW).list()
                .stream().map(this::view).toList();
    }

    /** Deck da sala para quem pede: mesma ordem para todos, sem o que essa pessoa já votou. */
    @Transactional(readOnly = true)
    public CursorResponse<TitleCard> deck(AuthUser user, String code, String cursor, int size) {
        Room room = participantRoom(user, code, false);
        requireStarted(room);
        Cursor after = Cursor.decode(cursor);
        Map<String, Object> params = new HashMap<>();
        params.put("seed", room.seed());
        params.put("room", room.id());
        params.put("me", user.id());
        StringBuilder sql = new StringBuilder("""
                SELECT id, h FROM (
                    SELECT t.id, md5(:seed || ':' || t.id) AS h
                    FROM title t WHERE t.overview IS NOT NULL
                      AND NOT EXISTS (SELECT 1 FROM match_vote v
                                      WHERE v.room_id = :room AND v.user_id = :me AND v.title_id = t.id)""");
        sql.append(SqlFilters.typeAndGenre(room.mediaType(), room.genreSlug(), params));
        sql.append(") s");
        if (after != null) {
            sql.append(" WHERE (h, id) > (:ch, :cid)");
            params.put("ch", after.key());
            params.put("cid", after.id());
        }
        sql.append(" ORDER BY h, id LIMIT :limit");
        params.put("limit", size + 1);

        record Row(long id, String key) {}
        List<Row> rows = jdbc.sql(sql.toString()).params(params)
                .query((rs, n) -> new Row(rs.getLong("id"), rs.getString("h")))
                .list();
        boolean hasMore = rows.size() > size;
        List<Row> page = hasMore ? rows.subList(0, size) : rows;
        String next = hasMore ? new Cursor(page.getLast().key(), page.getLast().id()).encode() : null;
        return new CursorResponse<>(cards.load(page.stream().map(Row::id).toList()), next);
    }

    // ------------------------------------------------------------------ regras

    /**
     * Procura um título que todos os participantes (2 ou mais) curtiram. Se houver mais de um
     * (pode acontecer quando alguém sai), vence o que vem primeiro no deck.
     */
    private Room settle(Room room) {
        int participants = participantCount(room.id());
        if (participants < MIN_PARTICIPANTS) return room;
        Optional<Long> winner = jdbc.sql("""
                SELECT title_id FROM match_vote
                WHERE room_id = :r AND liked
                GROUP BY title_id
                HAVING count(*) = :n
                ORDER BY md5(:seed || ':' || title_id), title_id
                LIMIT 1""")
                .param("r", room.id()).param("n", participants).param("seed", room.seed())
                .query(Long.class).optional();
        if (winner.isEmpty()) return room;
        Instant now = clock.instant();
        jdbc.sql("UPDATE match_room SET winner_title_id = :t, matched_at = :at WHERE id = :r")
                .param("t", winner.get()).param("at", Timestamp.from(now)).param("r", room.id()).update();
        return room.withWinner(winner.get(), now);
    }

    private static void requireStarted(Room room) {
        if (!room.started()) throw new ConflictException("A sala ainda não começou.");
    }

    // ------------------------------------------------------------------ leitura

    private static final String ROOM_SELECT = """
            SELECT r.id, r.code, r.owner_id, r.media_type, r.genre_slug, r.seed, r.mascot, r.winner_title_id,
                   r.started_at, r.matched_at, r.created_at
            FROM match_room r""";

    private Room lockedRoom(String code) {
        return findRoom("WHERE r.code = :key FOR UPDATE", normalize(code))
                .orElseThrow(() -> new NotFoundException("Sala " + normalize(code) + " não encontrada."));
    }

    private Room lockedRoomById(long id) {
        return findRoom("WHERE r.id = :key FOR UPDATE", id).orElseThrow();
    }

    private Room lockedParticipantRoom(AuthUser user, String code) {
        return participantRoom(user, code, true);
    }

    /** Quem não está na sala recebe 404, como se ela não existisse. */
    private Room participantRoom(AuthUser user, String code, boolean lock) {
        String c = normalize(code);
        Room room = findRoom("WHERE r.code = :key" + (lock ? " FOR UPDATE" : ""), c)
                .orElseThrow(() -> new NotFoundException("Sala " + c + " não encontrada."));
        if (!isParticipant(room.id(), user.id())) throw new NotFoundException("Sala " + c + " não encontrada.");
        return room;
    }

    private boolean isParticipant(long roomId, long userId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM match_participant WHERE room_id = :r AND user_id = :u)")
                .param("r", roomId).param("u", userId).query(Boolean.class).single();
    }

    private int participantCount(long roomId) {
        return jdbc.sql("SELECT count(*) FROM match_participant WHERE room_id = :r")
                .param("r", roomId).query(Integer.class).single();
    }

    private Optional<Room> findRoom(String where, Object key) {
        return jdbc.sql(ROOM_SELECT + " " + where).param("key", key).query(ROOM_ROW).optional();
    }

    private static final RowMapper<Room> ROOM_ROW = (rs, n) -> new Room(
            rs.getLong("id"), rs.getString("code"), rs.getLong("owner_id"),
            mediaType(rs.getString("media_type")), rs.getString("genre_slug"), rs.getString("seed"),
            rs.getInt("mascot"), (Long) rs.getObject("winner_title_id"), instant(rs.getTimestamp("started_at")),
            instant(rs.getTimestamp("matched_at")), instant(rs.getTimestamp("created_at")));

    private RoomResponse view(Room room) {
        List<Participant> participants = jdbc.sql("""
                SELECT %s, p.joined_at,
                       count(v.title_id) AS votes, count(v.title_id) FILTER (WHERE v.liked) AS likes
                FROM match_participant p
                JOIN app_user u ON u.id = p.user_id
                LEFT JOIN match_vote v ON v.room_id = p.room_id AND v.user_id = p.user_id
                WHERE p.room_id = :r
                GROUP BY u.id, p.joined_at
                ORDER BY p.joined_at, u.id""".formatted(UserRef.COLUMNS))
                .param("r", room.id())
                .query((rs, n) -> new Participant(
                        UserRef.from(rs),
                        rs.getInt("votes"), rs.getInt("likes"), instant(rs.getTimestamp("joined_at"))))
                .list();
        UserRef owner = participants.stream().map(Participant::user)
                .filter(u -> u.id() == room.ownerId()).findFirst()
                .orElseGet(() -> user(room.ownerId()));
        TitleCard winner = room.winnerId() == null ? null
                : cards.load(List.of(room.winnerId())).stream().findFirst().orElse(null);
        RoomStatus status = room.winnerId() != null ? RoomStatus.MATCHED
                : room.started() ? RoomStatus.VOTING : RoomStatus.WAITING;
        return new RoomResponse(room.code(), room.mediaType(),
                room.genreSlug() == null ? null : genre(room.genreSlug()),
                owner, participants, status, room.mascot(), winner,
                room.startedAt(), room.matchedAt(), room.createdAt());
    }

    private void addParticipant(long roomId, long userId) {
        jdbc.sql("INSERT INTO match_participant (room_id, user_id) VALUES (:r, :u) ON CONFLICT DO NOTHING")
                .param("r", roomId).param("u", userId).update();
    }

    private GenreRef genre(String slug) {
        return jdbc.sql("SELECT id, name, slug FROM genre WHERE slug = :s")
                .param("s", slug)
                .query((rs, n) -> new GenreRef(rs.getLong("id"), rs.getString("name"), rs.getString("slug")))
                .optional().orElse(null);
    }

    private UserRef user(long id) {
        return jdbc.sql("SELECT " + UserRef.COLUMNS + " FROM app_user u WHERE u.id = :id")
                .param("id", id)
                .query((rs, n) -> UserRef.from(rs))
                .single();
    }

    private String randomString(String alphabet, int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return sb.toString();
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    private static MediaType mediaType(String value) {
        return value == null ? null : MediaType.valueOf(value);
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}

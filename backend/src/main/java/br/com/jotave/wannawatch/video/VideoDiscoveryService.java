package br.com.jotave.wannawatch.video;

import br.com.jotave.wannawatch.catalog.application.ImageUrls;
import br.com.jotave.wannawatch.catalog.application.SqlFilters;
import br.com.jotave.wannawatch.shared.web.BadRequestException;
import br.com.jotave.wannawatch.shared.web.NotFoundException;
import br.com.jotave.wannawatch.video.VideoDtos.DiscoverRequest;
import br.com.jotave.wannawatch.video.VideoDtos.DiscoverResult;
import br.com.jotave.wannawatch.video.VideoDtos.DiscoverSource;
import br.com.jotave.wannawatch.video.VideoDtos.TitleVideos;
import br.com.jotave.wannawatch.video.VideoDtos.VideoStatus;
import br.com.jotave.wannawatch.video.VideoDtos.VideoView;
import br.com.jotave.wannawatch.video.youtube.YoutubeClient;
import br.com.jotave.wannawatch.video.youtube.YoutubeDto;
import br.com.jotave.wannawatch.video.youtube.YoutubeException;
import br.com.jotave.wannawatch.video.youtube.YoutubeProperties;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Busca vídeos curtos dos títulos na API do YouTube e guarda os metadados em {@code title_video}
 * como PENDING, para revisão. Só ficam os verticais (o Reels é em tela cheia, 9:16); a busca não
 * filtra por formato, então traz vários e descarta os horizontais. Roda sob demanda (tela de
 * revisão) e todo dia pelo GitHub Actions ({@code .github/workflows/videos.yml}), poucos títulos por vez.
 */
@Service
public class VideoDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(VideoDiscoveryService.class);

    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 20;

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final YoutubeClient youtube;
    private final YoutubeProperties props;
    private final ImageUrls images;

    public VideoDiscoveryService(JdbcClient jdbc, TransactionTemplate tx, YoutubeClient youtube,
                                 YoutubeProperties props, ImageUrls images) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.youtube = youtube;
        this.props = props;
        this.images = images;
    }

    record TitleRow(long id, String title, String mediaType, Integer year) {}

    public DiscoverResult discover(DiscoverRequest req) {
        if (!youtube.enabled()) {
            throw new BadRequestException("YOUTUBE_API_KEY não configurada no .env.");
        }
        if (req.yearFrom() != null && req.yearTo() != null && req.yearFrom() > req.yearTo()) {
            throw new BadRequestException("O ano inicial não pode ser depois do ano final.");
        }
        int n = Math.clamp(req.limit() == null ? DEFAULT_LIMIT : req.limit(), 1, MAX_LIMIT);
        List<TitleRow> titles = pickTitles(n, req);

        int searched = 0;
        int found = 0;
        int skipped = 0;
        for (TitleRow t : titles) {
            String query = query(t);
            try {
                List<YoutubeDto.Video> all = youtube.videos(youtube.searchShortVideos(query));
                List<YoutubeDto.Video> vertical = all.stream().filter(VideoDiscoveryService::isVertical).toList();
                tx.executeWithoutResult(s -> save(t.id(), query, vertical));
                searched++;
                found += vertical.size();
                skipped += all.size() - vertical.size();
            } catch (YoutubeException e) {
                log.warn("Busca de vídeos parou em \"{}\": {}", query, e.getMessage());
                return new DiscoverResult(searched, found, skipped, e.getMessage(), e.quotaExceeded());
            }
        }
        return new DiscoverResult(searched, found, skipped, null, false);
    }

    private List<TitleRow> pickTitles(int n, DiscoverRequest req) {
        String select = """
                SELECT t.id, t.title, t.media_type, CAST(EXTRACT(YEAR FROM t.release_date) AS INTEGER) AS year
                FROM title t""";
        if (req.titleIds() != null && !req.titleIds().isEmpty()) {
            return jdbc.sql(select + " WHERE t.id IN (:ids) ORDER BY t.popularity DESC, t.id DESC LIMIT :n")
                    .param("ids", req.titleIds()).param("n", n)
                    .query(TitleRow.class).list();
        }
        Map<String, Object> params = new HashMap<>();
        params.put("n", n);
        DiscoverSource source = req.source() == null ? DiscoverSource.POPULAR : req.source();
        StringBuilder sql = new StringBuilder(select);
        if (source == DiscoverSource.TRENDING) {
            sql.append(" JOIN title_ranking r ON r.title_id = t.id AND r.list_key = 'trending_day'");
        }
        sql.append(" WHERE TRUE");
        if (!Boolean.TRUE.equals(req.includeSearched())) {
            sql.append(" AND NOT EXISTS (SELECT 1 FROM title_video_search s WHERE s.title_id = t.id)");
        }
        if (!Boolean.FALSE.equals(req.skipApproved())) {
            sql.append(" AND NOT EXISTS (SELECT 1 FROM title_video v"
                    + " WHERE v.title_id = t.id AND v.status = 'APPROVED')");
        }
        sql.append(SqlFilters.typeAndGenre(req.type(), req.genre(), params));
        if (req.yearFrom() != null) {
            sql.append(" AND t.release_date >= make_date(:yearFrom, 1, 1)");
            params.put("yearFrom", req.yearFrom());
        }
        if (req.yearTo() != null) {
            sql.append(" AND t.release_date < make_date(:yearAfter, 1, 1)");
            params.put("yearAfter", req.yearTo() + 1);
        }
        sql.append(switch (source) {
            case POPULAR -> " ORDER BY t.popularity DESC, t.id DESC";
            case TRENDING -> " ORDER BY r.position";
            // nota alta com poucos votos não diz muito: exige um mínimo
            case TOP_RATED -> " AND t.vote_count >= 100"
                    + " ORDER BY t.vote_average DESC NULLS LAST, t.vote_count DESC, t.id DESC";
            case RECENT -> " AND t.release_date <= CURRENT_DATE ORDER BY t.release_date DESC, t.id DESC";
        });
        sql.append(" LIMIT :n");
        return jdbc.sql(sql.toString()).params(params).query(TitleRow.class).list();
    }

    /**
     * "Duna trailer #shorts" / "Round 6 série trailer #shorts": o #shorts puxa os vídeos verticais
     * (o ano ficou de fora: quase nenhum Short põe o ano no título).
     */
    static String query(TitleRow t) {
        StringBuilder q = new StringBuilder(t.title());
        if ("SERIES".equals(t.mediaType())) q.append(" série");
        return q.append(" trailer #shorts").toString();
    }

    /** O player vem sem tarjas, na proporção real: mais alto que largo = vertical. */
    static boolean isVertical(YoutubeDto.Video v) {
        var p = v.player();
        return p != null && p.embedWidth() != null && p.embedHeight() != null && p.embedHeight() > p.embedWidth();
    }

    private void save(long titleId, String query, List<YoutubeDto.Video> videos) {
        Instant now = Instant.now();
        for (int rank = 0; rank < videos.size(); rank++) {
            YoutubeDto.Video v = videos.get(rank);
            var snippet = v.snippet();
            var details = v.contentDetails();
            var player = v.player();
            jdbc.sql("""
                    INSERT INTO title_video (title_id, source, platform, external_key, video_title,
                        channel_title, published_at, duration_seconds, width, height, embeddable,
                        blocked_in_region, search_rank, checked_at)
                    VALUES (:title, 'YOUTUBE_SEARCH', 'YOUTUBE', :key, :videoTitle, :channel, :published,
                        :duration, :width, :height, :embeddable, :blocked, :rank, :now)
                    ON CONFLICT (title_id, platform, external_key) DO UPDATE SET
                        video_title = EXCLUDED.video_title, channel_title = EXCLUDED.channel_title,
                        published_at = EXCLUDED.published_at, duration_seconds = EXCLUDED.duration_seconds,
                        width = EXCLUDED.width, height = EXCLUDED.height, embeddable = EXCLUDED.embeddable,
                        blocked_in_region = EXCLUDED.blocked_in_region, search_rank = EXCLUDED.search_rank,
                        checked_at = EXCLUDED.checked_at, updated_at = now()""")
                    .param("title", titleId)
                    .param("key", v.id())
                    .param("videoTitle", snippet == null ? null : truncate(snippet.title(), 500))
                    .param("channel", snippet == null ? null : truncate(snippet.channelTitle(), 255))
                    .param("published", snippet == null || snippet.publishedAt() == null
                            ? null : Timestamp.from(snippet.publishedAt()))
                    .param("duration", details == null ? null : seconds(details.duration()))
                    .param("width", player == null || player.embedWidth() == null ? null : player.embedWidth().intValue())
                    .param("height", player == null || player.embedHeight() == null ? null : player.embedHeight().intValue())
                    .param("embeddable", v.status() == null || !Boolean.FALSE.equals(v.status().embeddable()))
                    .param("blocked", details != null && blockedIn(details.regionRestriction(), props.regionCode()))
                    .param("rank", rank)
                    .param("now", Timestamp.from(now))
                    .update();
        }
        jdbc.sql("""
                INSERT INTO title_video_search (title_id, query, results_count, searched_at)
                VALUES (:title, :query, :count, :now)
                ON CONFLICT (title_id) DO UPDATE SET query = EXCLUDED.query,
                    results_count = EXCLUDED.results_count, searched_at = EXCLUDED.searched_at""")
                .param("title", titleId)
                .param("query", truncate(query, 600))
                .param("count", videos.size())
                .param("now", Timestamp.from(now))
                .update();
    }

    static Integer seconds(String iso) {
        if (iso == null) return null;
        try {
            return Math.toIntExact(Duration.parse(iso).toSeconds());
        } catch (DateTimeParseException | ArithmeticException e) {
            return null;
        }
    }

    static boolean blockedIn(YoutubeDto.RegionRestriction r, String region) {
        if (r == null || region == null) return false;
        if (r.allowed() != null && !r.allowed().contains(region)) return true;
        return r.blocked() != null && r.blocked().contains(region);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    // ---------------------------------------------------------------- revisão

    record SearchRow(long titleId, String title, String mediaType, Integer year, String posterPath,
                     String query, int resultsCount, Instant searchedAt) {}

    record VideoRow(long id, long titleId, String externalKey, String videoTitle, String channelTitle,
                    Instant publishedAt, Integer durationSeconds, Integer width, Integer height,
                    boolean embeddable, boolean blockedInRegion, Integer searchRank, String status,
                    Instant reviewedAt) {}

    /** Títulos já buscados (mais recentes primeiro) com os vídeos de cada um. */
    public List<TitleVideos> list() {
        List<SearchRow> searches = jdbc.sql("""
                SELECT t.id AS title_id, t.title, t.media_type,
                       CAST(EXTRACT(YEAR FROM t.release_date) AS INTEGER) AS year, t.poster_path,
                       s.query, s.results_count, s.searched_at
                FROM title_video_search s JOIN title t ON t.id = s.title_id
                ORDER BY s.searched_at DESC, t.popularity DESC, t.id DESC""")
                .query(SearchRow.class).list();
        if (searches.isEmpty()) return List.of();

        Map<Long, List<VideoView>> byTitle = jdbc.sql("""
                SELECT id, title_id, external_key, video_title, channel_title, published_at,
                       duration_seconds, width, height, embeddable, blocked_in_region, search_rank, status,
                       reviewed_at
                FROM title_video WHERE title_id IN (:ids)
                ORDER BY title_id, search_rank NULLS LAST, id""")
                .param("ids", searches.stream().map(SearchRow::titleId).toList())
                .query(VideoRow.class).list().stream()
                .collect(Collectors.groupingBy(VideoRow::titleId,
                        Collectors.mapping(VideoDiscoveryService::view, Collectors.toList())));

        return searches.stream().map(s -> new TitleVideos(s.titleId(), s.title(), s.mediaType(), s.year(),
                images.poster(s.posterPath()), s.query(), s.resultsCount(), s.searchedAt(),
                byTitle.getOrDefault(s.titleId(), List.of()))).toList();
    }

    public void setStatus(long videoId, VideoStatus status) {
        if (status == null) throw new BadRequestException("Informe o status.");
        int updated = jdbc.sql("""
                UPDATE title_video
                SET status = :s, updated_at = now(),
                    reviewed_at = CASE WHEN :s = 'PENDING' THEN NULL ELSE now() END
                WHERE id = :id""")
                .param("s", status.name()).param("id", videoId).update();
        if (updated == 0) throw new NotFoundException("Vídeo não encontrado.");
    }

    private static VideoView view(VideoRow r) {
        boolean vertical = r.width() != null && r.height() != null && r.height() > r.width();
        return new VideoView(r.id(), r.externalKey(), r.videoTitle(), r.channelTitle(), r.publishedAt(),
                r.durationSeconds(), r.width(), r.height(), vertical, r.embeddable(), r.blockedInRegion(),
                r.searchRank(), VideoStatus.valueOf(r.status()), r.reviewedAt());
    }
}

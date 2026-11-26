package br.com.jotave.wannawatch.video;

import br.com.jotave.wannawatch.catalog.application.ImageUrls;
import br.com.jotave.wannawatch.shared.web.BadRequestException;
import br.com.jotave.wannawatch.shared.web.NotFoundException;
import br.com.jotave.wannawatch.video.VideoDtos.DiscoverResult;
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
 * como PENDING, para revisão. Fase de teste: roda só sob demanda, poucos títulos por vez.
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

    public DiscoverResult discover(Integer limit, List<Long> titleIds) {
        if (!youtube.enabled()) {
            throw new BadRequestException("YOUTUBE_API_KEY não configurada no .env.");
        }
        int n = Math.clamp(limit == null ? DEFAULT_LIMIT : limit, 1, MAX_LIMIT);
        List<TitleRow> titles = pickTitles(n, titleIds);

        int searched = 0;
        int found = 0;
        for (TitleRow t : titles) {
            String query = query(t);
            try {
                List<YoutubeDto.Video> videos = youtube.videos(youtube.searchShortVideos(query));
                tx.executeWithoutResult(s -> save(t.id(), query, videos));
                searched++;
                found += videos.size();
            } catch (YoutubeException e) {
                log.warn("Busca de vídeos parou em \"{}\": {}", query, e.getMessage());
                return new DiscoverResult(searched, found, e.getMessage());
            }
        }
        return new DiscoverResult(searched, found, null);
    }

    private List<TitleRow> pickTitles(int n, List<Long> titleIds) {
        String select = """
                SELECT id, title, media_type, CAST(EXTRACT(YEAR FROM release_date) AS INTEGER) AS year
                FROM title""";
        if (titleIds != null && !titleIds.isEmpty()) {
            return jdbc.sql(select + " WHERE id IN (:ids) ORDER BY popularity DESC, id DESC LIMIT :n")
                    .param("ids", titleIds).param("n", n)
                    .query(TitleRow.class).list();
        }
        return jdbc.sql(select + """
                 t WHERE NOT EXISTS (SELECT 1 FROM title_video_search s WHERE s.title_id = t.id)
                ORDER BY popularity DESC, id DESC LIMIT :n""")
                .param("n", n)
                .query(TitleRow.class).list();
    }

    /** "Duna 2021 trailer" / "Round 6 série trailer": o ano e o tipo ajudam a não cair em homônimos. */
    static String query(TitleRow t) {
        StringBuilder q = new StringBuilder(t.title());
        if ("SERIES".equals(t.mediaType())) q.append(" série");
        if (t.year() != null) q.append(' ').append(t.year());
        return q.append(" trailer").toString();
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
                    boolean embeddable, boolean blockedInRegion, Integer searchRank, String status) {}

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
                       duration_seconds, width, height, embeddable, blocked_in_region, search_rank, status
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
        int updated = jdbc.sql("UPDATE title_video SET status = :s, updated_at = now() WHERE id = :id")
                .param("s", status.name()).param("id", videoId).update();
        if (updated == 0) throw new NotFoundException("Vídeo não encontrado.");
    }

    private static VideoView view(VideoRow r) {
        boolean vertical = r.width() != null && r.height() != null && r.height() > r.width();
        return new VideoView(r.id(), r.externalKey(), r.videoTitle(), r.channelTitle(), r.publishedAt(),
                r.durationSeconds(), r.width(), r.height(), vertical, r.embeddable(), r.blockedInRegion(),
                r.searchRank(), VideoStatus.valueOf(r.status()));
    }
}

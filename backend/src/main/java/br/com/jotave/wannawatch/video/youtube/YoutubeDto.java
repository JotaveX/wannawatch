package br.com.jotave.wannawatch.video.youtube;

import java.time.Instant;
import java.util.List;

/** Só os campos da YouTube Data API v3 que usamos (o resto é ignorado). */
public final class YoutubeDto {

    private YoutubeDto() {}

    public record SearchResponse(List<SearchItem> items) {}

    public record SearchItem(SearchId id) {}

    public record SearchId(String videoId) {}

    public record VideoListResponse(List<Video> items) {}

    public record Video(String id, Snippet snippet, ContentDetails contentDetails, Status status, Player player) {}

    public record Snippet(String title, String channelTitle, Instant publishedAt) {}

    /** {@code duration} em ISO 8601 (ex.: "PT1M5S"). */
    public record ContentDetails(String duration, RegionRestriction regionRestriction) {}

    /** Se {@code allowed} vier, só esses países podem ver; se {@code blocked} vier, esses não podem. */
    public record RegionRestriction(List<String> allowed, List<String> blocked) {}

    public record Status(Boolean embeddable, String privacyStatus) {}

    /** Dimensões do player sem tarjas; só vêm quando a requisição manda maxHeight/maxWidth. */
    public record Player(Long embedWidth, Long embedHeight) {}
}

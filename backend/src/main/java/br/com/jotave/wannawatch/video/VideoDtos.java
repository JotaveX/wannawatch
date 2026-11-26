package br.com.jotave.wannawatch.video;

import br.com.jotave.wannawatch.catalog.api.CatalogDtos.ImageSet;
import java.time.Instant;
import java.util.List;

public final class VideoDtos {

    private VideoDtos() {}

    public enum VideoStatus { PENDING, APPROVED, REJECTED }

    /**
     * @param limit    quantos títulos buscar (1 a 20; padrão 10). Cada busca gasta 1 das 100 do dia.
     * @param titleIds títulos específicos (busca de novo mesmo os já buscados); vazio = os mais
     *                 populares ainda não buscados.
     */
    public record DiscoverRequest(Integer limit, List<Long> titleIds) {}

    /** @param error preenchido quando a rodada parou no meio (ex.: cota acabou). */
    public record DiscoverResult(int titlesSearched, int videosFound, String error) {}

    public record StatusRequest(VideoStatus status) {}

    /** Um título já buscado no YouTube, com o que a busca achou. */
    public record TitleVideos(
            long titleId,
            String title,
            String mediaType,
            Integer year,
            ImageSet poster,
            String query,
            int resultsCount,
            Instant searchedAt,
            List<VideoView> videos) {}

    public record VideoView(
            long id,
            String externalKey,
            String videoTitle,
            String channelTitle,
            Instant publishedAt,
            Integer durationSeconds,
            Integer width,
            Integer height,
            boolean vertical,
            boolean embeddable,
            boolean blockedInRegion,
            Integer searchRank,
            VideoStatus status) {}
}

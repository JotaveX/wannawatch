package br.com.jotave.wannawatch.video;

import br.com.jotave.wannawatch.catalog.api.CatalogDtos.ImageSet;
import br.com.jotave.wannawatch.catalog.domain.MediaType;
import java.time.Instant;
import java.util.List;

public final class VideoDtos {

    private VideoDtos() {}

    public enum VideoStatus { PENDING, APPROVED, REJECTED }

    /** De onde saem os títulos da busca: a ordem (e, em "em alta", a lista) usada para escolher. */
    public enum DiscoverSource { POPULAR, TRENDING, TOP_RATED, RECENT }

    /**
     * @param limit           quantos títulos buscar (1 a 20; padrão 10). Cada busca gasta 1 das 100 do dia.
     * @param titleIds        títulos específicos (busca de novo mesmo os já buscados; ignora os filtros);
     *                        vazio = escolhe pelos filtros abaixo.
     * @param skipApproved    pula títulos que já têm vídeo aprovado (padrão: sim)
     * @param includeSearched busca de novo títulos que já foram buscados (padrão: não)
     * @param source          ordem/lista de onde saem os títulos (padrão: POPULAR)
     * @param type            só filmes ou só séries (null = os dois)
     * @param genre           slug do gênero (null = todos)
     * @param yearFrom        lançados a partir deste ano
     * @param yearTo          lançados até este ano
     */
    public record DiscoverRequest(Integer limit, List<Long> titleIds, Boolean skipApproved,
                                  Boolean includeSearched, DiscoverSource source, MediaType type,
                                  String genre, Integer yearFrom, Integer yearTo) {

        public static DiscoverRequest defaults() {
            return new DiscoverRequest(null, null, null, null, null, null, null, null, null);
        }
    }

    /**
     * @param videosFound        vídeos verticais guardados
     * @param horizontalSkipped  vídeos que a busca trouxe mas eram horizontais (descartados)
     * @param error              preenchido quando a rodada parou no meio (ex.: cota acabou)
     * @param quotaExceeded      a rodada parou porque a cota diária do YouTube acabou
     */
    public record DiscoverResult(int titlesSearched, int videosFound, int horizontalSkipped, String error,
                                 boolean quotaExceeded) {}

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

    /** @param reviewedAt quando foi aprovado ou reprovado (null enquanto pendente) */
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
            VideoStatus status,
            Instant reviewedAt) {}
}

package br.com.jotave.wannawatch.tmdb.importer;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.jotave.wannawatch.catalog.domain.CreditType;
import br.com.jotave.wannawatch.catalog.domain.MediaType;
import br.com.jotave.wannawatch.catalog.domain.TitleDetails;
import br.com.jotave.wannawatch.tmdb.TmdbFixtures;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TmdbMapperTest {

    private final TmdbMapper mapper = new TmdbMapper("pt-BR", "BR");

    @Nested
    class Filme {

        private final ImportedTitle movie = mapper.fromMovie(TmdbFixtures.movie101());

        @Test
        void mapeiaCamposBasicosEmPtBr() {
            assertThat(movie.externalId()).isEqualTo(101);
            assertThat(movie.mediaType()).isEqualTo(MediaType.MOVIE);
            assertThat(movie.title()).isEqualTo("Noite em Marte");
            assertThat(movie.originalTitle()).isEqualTo("Night on Mars");
            assertThat(movie.tagline()).isEqualTo("Ninguém fica sozinho");
            assertThat(movie.overview()).startsWith("Uma astronauta");
            assertThat(movie.releaseDate()).isEqualTo(LocalDate.of(2026, 3, 12));
            assertThat(movie.runtimeMinutes()).isEqualTo(130);
            assertThat(movie.seasonsCount()).isNull();
            assertThat(movie.popularity()).isEqualTo(250.5);
            assertThat(movie.genreExternalIds()).containsExactly(878, 18);
        }

        @Test
        void valoresZeradosDaTmdbViramNulo() {
            assertThat(movie.budget()).isNull();
            assertThat(movie.revenue()).isEqualTo(123_000_000L);
        }

        @Test
        void classificacaoBrPrefereALancamentoNoCinema() {
            assertThat(movie.ageRating()).isEqualTo("12");
        }

        @Test
        void trailerPrefereOficialDoYoutubeEmPortugues() {
            assertThat(movie.trailerYoutubeKey()).isEqualTo("PT_TRAILER");
        }

        @Test
        void elencoVemOrdenadoEEquipeSoComAsFuncoesQueUsamos() {
            List<ImportedTitle.Credit> cast = movie.credits().stream()
                    .filter(c -> c.type() == CreditType.CAST).toList();
            assertThat(cast).extracting(ImportedTitle.Credit::name)
                    .containsExactly("Ana Protagonista", "Caio Segundo", "Bia Coadjuvante");
            assertThat(cast).extracting(ImportedTitle.Credit::order).containsExactly(0, 1, 2);

            List<ImportedTitle.Credit> crew = movie.credits().stream()
                    .filter(c -> c.type() == CreditType.CREW).toList();
            assertThat(crew).extracting(ImportedTitle.Credit::job)
                    .containsExactly("Director", "Screenplay"); // sem Producer e sem diretor duplicado
        }

        @Test
        void ondeAssistirSoComOsServicosDoBrasilNaOrdemDaTmdb() {
            TitleDetails.WatchProviders wp = movie.details().watchProviders();
            assertThat(wp.link()).contains("locale=BR");
            assertThat(wp.streaming()).extracting(TitleDetails.NamedLogo::name).containsExactly("Max", "Netflix");
            assertThat(wp.rent()).extracting(TitleDetails.NamedLogo::name).containsExactly("Apple TV");
            assertThat(wp.buy()).isEmpty();
        }

        @Test
        void fichaTecnicaVaiParaOsDetalhes() {
            assertThat(movie.details().productionCompanies())
                    .containsExactly(new TitleDetails.NamedLogo("Estúdio Vermelho", "/estudio.png"));
            assertThat(movie.details().productionCountries())
                    .containsExactly(new TitleDetails.Country("US", "United States of America"));
        }
    }

    @Nested
    class Serie {

        private final ImportedTitle tv = mapper.fromTv(TmdbFixtures.tv202());

        @Test
        void mapeiaCamposDeSerie() {
            assertThat(tv.mediaType()).isEqualTo(MediaType.SERIES);
            assertThat(tv.title()).isEqualTo("Os Vizinhos de Cima");
            assertThat(tv.releaseDate()).isEqualTo(LocalDate.of(2024, 5, 1));
            assertThat(tv.seasonsCount()).isEqualTo(2);
            assertThat(tv.episodesCount()).isEqualTo(16);
            assertThat(tv.runtimeMinutes()).isNull();
            assertThat(tv.details().networks()).extracting(TitleDetails.NamedLogo::name).containsExactly("HBO");
        }

        @Test
        void sinopseVaziaCaiParaOInglesAmericano() {
            assertThat(tv.overview()).isEqualTo("Three friends share a tiny flat.");
        }

        @Test
        void taglineVaziaFicaNula() {
            assertThat(tv.tagline()).isNull();
        }

        @Test
        void semClassificacaoBrFicaNula() {
            assertThat(tv.ageRating()).isNull();
        }

        @Test
        void semTrailerESemOndeAssistir() {
            assertThat(tv.trailerYoutubeKey()).isNull();
            assertThat(tv.details().watchProviders()).isEqualTo(TitleDetails.WatchProviders.none());
        }

        @Test
        void criadoresEntramComoEquipeAntesDaDirecao() {
            List<ImportedTitle.Credit> crew = tv.credits().stream()
                    .filter(c -> c.type() == CreditType.CREW).toList();
            assertThat(crew).extracting(ImportedTitle.Credit::name, ImportedTitle.Credit::job)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("Gabi Criadora", "Creator"),
                            org.assertj.core.groups.Tuple.tuple("Davi Diretor", "Director"));
        }
    }

    @Test
    void sinopseSemNenhumaTraducaoFicaNula() {
        var original = TmdbFixtures.tv202();
        var semTraducao = new br.com.jotave.wannawatch.tmdb.client.TmdbDto.TvDetails(
                original.id(), original.name(), original.originalName(), original.tagline(), "  ",
                original.originalLanguage(), original.firstAirDate(), original.numberOfSeasons(),
                original.numberOfEpisodes(), original.episodeRunTime(), original.status(), original.posterPath(),
                original.backdropPath(), original.voteAverage(), original.voteCount(), original.popularity(),
                original.adult(), original.genres(), original.productionCompanies(), original.productionCountries(),
                original.networks(), original.createdBy(), original.contentRatings(), original.videos(),
                original.credits(), null, original.watchProviders());
        assertThat(mapper.fromTv(semTraducao).overview()).isNull();
    }

    @Test
    void slugTiraAcentoETrocaEComercial() {
        assertThat(TmdbMapper.slug("Ficção científica")).isEqualTo("ficcao-cientifica");
        assertThat(TmdbMapper.slug("Sci-Fi & Fantasy")).isEqualTo("sci-fi-e-fantasy");
        assertThat(TmdbMapper.slug("Ação")).isEqualTo("acao");
    }
}

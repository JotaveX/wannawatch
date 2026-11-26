package br.com.jotave.wannawatch.recommendation.domain;

import static br.com.jotave.wannawatch.recommendation.domain.TitleBuilder.index;
import static br.com.jotave.wannawatch.recommendation.domain.TitleBuilder.title;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReasonResolverTest {

    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
    private final ReasonResolver resolver = new ReasonResolver(0.35);

    private Reason reason(TitleFeatureIndex idx, long target, Interaction... interactions) {
        TasteProfile p = TasteProfile.build(List.of(interactions), null, idx, SignalWeights.defaults(), NOW);
        return resolver.resolve(p, idx, idx.get(target).orElseThrow());
    }

    private static Interaction liked(long id) {
        return new Interaction(id, InteractionType.LIKE, NOW);
    }

    @Test
    void parecidoComUmCurtidoDizQualFoi() {
        var idx = index(title(1).named("Dark").series().genres(1, 2).keywords(7), title(2).series().genres(1, 2).keywords(7),
                title(3).genres(3), title(4).genres(4).keywords(7));
        assertThat(reason(idx, 2, liked(1))).isEqualTo(new Reason(ReasonKind.BECAUSE_LIKED, "Porque você curtiu Dark"));
    }

    @Test
    void salvoTambemServeDeAncora() {
        var idx = index(title(1).named("Dark").series().genres(1, 2), title(2).series().genres(1, 2), title(3).genres(3));
        Reason r = reason(idx, 2, new Interaction(1, InteractionType.SAVED, NOW));
        assertThat(r.kind()).isEqualTo(ReasonKind.BECAUSE_LIKED);
    }

    @Test
    void pessoaQueMaisContribuiViraDeOuCom() {
        // curtiu um votado no Match (não é âncora) dirigido por Nolan → título com Nolan na direção
        var idx = index(title(1).genres(5).director(100).language("ja").year(1980),
                title(2).genres(3).director(100).cast(200, 0), title(3).genres(2).cast(200, 0), title(4).genres(4));
        Reason r = reason(idx, 2, new Interaction(1, InteractionType.MATCH_YES, NOW));
        assertThat(r).isEqualTo(new Reason(ReasonKind.PERSON, "De Christopher Nolan"));

        Reason ator = reason(idx, 3, new Interaction(2, InteractionType.MATCH_YES, NOW));
        assertThat(ator).isEqualTo(new Reason(ReasonKind.PERSON, "Com Pedro Pascal"));
    }

    @Test
    void generoQuandoNaoHaPessoaEmComum() {
        var idx = index(title(1).genres(1).language("ja"), title(2).genres(1, 3).language("ko"), title(3).genres(4));
        Reason r = reason(idx, 2, new Interaction(1, InteractionType.MATCH_YES, NOW));
        assertThat(r).isEqualTo(new Reason(ReasonKind.GENRE, "Você curte ficção científica"));
    }

    @Test
    void semNadaEmComumCaiNaPopularidadeOuNaNota() {
        var idx = index(title(1).genres(1).popularity(900).votes(6, 100), title(2).genres(2).popularity(1).votes(9, 50000));
        assertThat(reason(idx, 1)).isEqualTo(new Reason(ReasonKind.POPULAR, "Em alta"));
        assertThat(reason(idx, 2)).isEqualTo(new Reason(ReasonKind.POPULAR, "Bem avaliado"));
    }
}

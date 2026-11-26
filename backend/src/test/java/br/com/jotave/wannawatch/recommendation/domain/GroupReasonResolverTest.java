package br.com.jotave.wannawatch.recommendation.domain;

import static br.com.jotave.wannawatch.recommendation.domain.TitleBuilder.index;
import static br.com.jotave.wannawatch.recommendation.domain.TitleBuilder.title;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.jotave.wannawatch.recommendation.domain.GroupReasonResolver.Member;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GroupReasonResolverTest {

    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
    private final Scorer scorer = new Scorer(ScoringWeights.defaults());
    private final GroupReasonResolver resolver = new GroupReasonResolver(GroupParams.defaults());

    private final TitleFeatureIndex idx = index(title(1).genres(1), title(2).genres(1), title(3).genres(1),
            title(4).genres(1), title(5).genres(1), title(6).genres(1), title(7).genres(3).popularity(1),
            title(8).genres(3).popularity(1), title(9).genres(4).popularity(1), title(10).genres(4).popularity(1));

    private Member member(long id, String name, long... liked) {
        List<Interaction> list = new ArrayList<>();
        for (long t : liked) list.add(new Interaction(t, InteractionType.LIKE, NOW));
        TasteProfile p = TasteProfile.build(list, null, idx, SignalWeights.defaults(), NOW);
        return new Member(id, name, UserScores.compute(idx, p, scorer));
    }

    private Reason reason(long me, long title, Member... members) {
        return resolver.resolve(me, List.of(members), idx.get(title).orElseThrow());
    }

    @Test
    void bomParaTodosComVocePrimeiroEOsOutrosEmOrdemAlfabetica() {
        Member eu = member(1, "Jotave", 1, 2, 3, 4, 5);
        Member caio = member(3, "Caio", 1, 2, 3, 4, 5);
        Member bia = member(2, "Bia", 1, 2, 3, 4, 5);
        assertThat(reason(1, 6, eu, bia)).isEqualTo(new Reason(ReasonKind.GROUP, "Bom pra você e pra Bia"));
        assertThat(reason(1, 6, eu, caio, bia)).isEqualTo(new Reason(ReasonKind.GROUP, "Bom pra você, Bia e Caio"));
        assertThat(reason(2, 6, eu, bia).text()).isEqualTo("Bom pra você e pra Jotave");
    }

    @Test
    void quemQuerVer() {
        Member eu = member(1, "Jotave", 9);
        Member bia = member(2, "Bia", 7);
        Member caio = member(3, "Caio", 9);
        assertThat(reason(1, 7, eu, bia)).isEqualTo(new Reason(ReasonKind.WANTED_BY, "Bia quer ver"));
        assertThat(reason(1, 9, eu, bia, caio)).isEqualTo(new Reason(ReasonKind.WANTED_BY, "Você e Caio querem ver"));
        assertThat(reason(2, 9, eu, bia, caio).text()).isEqualTo("Caio e Jotave querem ver");
    }

    @Test
    void todoMundoCurteOGenero() {
        // os dois curtem ficção científica, mas o título 7 (comédia, impopular) não é bom pra ninguém
        Member eu = member(1, "Jotave", 1, 2, 7);
        Member bia = member(2, "Bia", 3, 4, 8);
        Reason r = reason(1, 10, eu, bia);
        assertThat(r).isNull();
        assertThat(reason(1, 6, member(1, "Jotave", 1), member(2, "Bia", 2)))
                .isIn(new Reason(ReasonKind.GENRE, "Todo mundo curte ficção científica"),
                        new Reason(ReasonKind.GROUP, "Bom pra você e pra Bia"));
    }

    @Test
    void juntaNomes() {
        assertThat(GroupReasonResolver.join(List.of("A"))).isEqualTo("A");
        assertThat(GroupReasonResolver.join(List.of("A", "B"))).isEqualTo("A e B");
        assertThat(GroupReasonResolver.join(List.of("A", "B", "C"))).isEqualTo("A, B e C");
    }
}

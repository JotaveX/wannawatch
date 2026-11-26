package br.com.jotave.wannawatch.recommendation.domain;

import static br.com.jotave.wannawatch.recommendation.domain.TitleBuilder.index;
import static br.com.jotave.wannawatch.recommendation.domain.TitleBuilder.title;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GroupRankingTest {

    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");
    private static final GroupParams SEM_RUIDO = new GroupParams(0.5, 0.25, 0.15, 0.20, 0, 0.6, 0.5);
    private final Scorer scorer = new Scorer(ScoringWeights.defaults());

    // 1..6 ficção científica, 7..9 comédia, 10..12 terror
    private final TitleFeatureIndex idx = index(title(1).genres(1), title(2).genres(1), title(3).genres(1),
            title(4).genres(1), title(5).genres(1), title(6).genres(1), title(7).genres(3), title(8).genres(3),
            title(9).genres(3), title(10).genres(4), title(11).genres(4), title(12).genres(4));

    private UserScores user(Object... pairs) {
        List<Interaction> list = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            list.add(new Interaction(((Number) pairs[i]).longValue(), (InteractionType) pairs[i + 1], NOW));
        }
        return UserScores.compute(idx, TasteProfile.build(list, null, idx, SignalWeights.defaults(), NOW), scorer);
    }

    private List<Long> order(List<UserScores> users) {
        List<Long> all = idx.entries().stream().map(TitleFeatureIndex.Entry::id).sorted().toList();
        return GroupRanking.rank(all, users, SEM_RUIDO, "s").stream().map(GroupRanking.Ranked::titleId).toList();
    }

    private static final InteractionType LIKE = InteractionType.LIKE;

    @Test
    void oQueOsDoisCurtemVemAntesDoQueSoUmCurte() {
        UserScores a = user(1, LIKE, 2, LIKE, 3, LIKE, 4, LIKE, 7, LIKE);
        UserScores b = user(1, LIKE, 2, LIKE, 3, LIKE, 4, LIKE, 5, LIKE);
        List<Long> order = order(List.of(a, b));
        assertThat(order.indexOf(6L)).isLessThan(order.indexOf(8L));
    }

    @Test
    void miseriaVaiParaOFim() {
        UserScores a = user(1, LIKE, 2, LIKE, 3, LIKE, 4, LIKE, 5, LIKE, 10, LIKE);
        UserScores b = user(1, LIKE, 2, LIKE, 3, LIKE, 7, LIKE, 8, LIKE,
                10, InteractionType.PASS, 11, InteractionType.PASS);
        List<Long> order = order(List.of(a, b));
        // 12 nunca foi votado, mas é parecido com o que b passou: fica atrás de tudo que os dois toleram
        for (long tolerado : List.of(1L, 2L, 3L, 4L, 5L, 6L)) {
            assertThat(order.indexOf(12L)).isGreaterThan(order.indexOf(tolerado));
        }
    }

    @Test
    void quemQuerVerDaUmEmpurraoEJaVistoPorTodosSai() {
        UserScores a = user(9, LIKE, 12, InteractionType.SEEN);
        UserScores b = user(12, InteractionType.SEEN);
        List<Long> order = order(List.of(a, b));
        assertThat(order).doesNotContain(12L).hasSize(11);
        assertThat(order.getFirst()).isEqualTo(9L);
    }

    @Test
    void ruidoMudaPoucoEDependeDaSeed() {
        UserScores a = user();
        List<Long> all = idx.entries().stream().map(TitleFeatureIndex.Entry::id).sorted().toList();
        var x = GroupRanking.rank(all, List.of(a, a), GroupParams.defaults(), "sala-1");
        var y = GroupRanking.rank(all, List.of(a, a), GroupParams.defaults(), "sala-1");
        assertThat(x).isEqualTo(y);
    }
}

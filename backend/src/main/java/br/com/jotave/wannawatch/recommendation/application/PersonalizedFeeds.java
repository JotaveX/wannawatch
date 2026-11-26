package br.com.jotave.wannawatch.recommendation.application;

import br.com.jotave.wannawatch.recommendation.domain.Exclusion;
import br.com.jotave.wannawatch.recommendation.domain.TasteProfile;
import br.com.jotave.wannawatch.recommendation.domain.TitleFeatureIndex;
import br.com.jotave.wannawatch.recommendation.domain.UserScores;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongPredicate;
import org.springframework.stereotype.Service;

/** Ordem personalizada do Swipe e do Reels a partir dos candidatos (já filtrados por tipo e gênero). */
@Service
public class PersonalizedFeeds {

    /** O Swipe não mostra o que a pessoa já avaliou; "talvez" volta depois do tempo de espera. */
    public static final Set<Exclusion> SWIPE_EXCLUSIONS =
            EnumSet.of(Exclusion.LIKED, Exclusion.SEEN, Exclusion.PASSED, Exclusion.SAVED, Exclusion.RECENT_MAYBE);
    /** Os Reels só tiram o que a pessoa já viu ou dispensou; os curtidos vão para o fim. */
    public static final Set<Exclusion> REELS_EXCLUSIONS = EnumSet.of(Exclusion.SEEN, Exclusion.PASSED);

    private final RecommendationService recommendations;
    private final RecommendationProperties props;

    public PersonalizedFeeds(RecommendationService recommendations, RecommendationProperties props) {
        this.recommendations = recommendations;
        this.props = props;
    }

    /**
     * {@code score + ruído·md5(seed:id)}, para cada visita variar um pouco, e a cada
     * {@code explorationEvery} cartas uma de exploração: bem avaliada, de um gênero com pouco peso
     * no perfil (tira a pessoa da bolha). Sem sinais positivos ainda não há bolha, então não explora.
     */
    public List<Long> swipeOrder(long userId, List<Long> candidates, String seed) {
        UserScores scores = recommendations.scores(userId);
        TasteProfile profile = scores.profile();
        LongPredicate excluded = recommendations.excluded(profile, SWIPE_EXCLUSIONS);
        RecommendationProperties.Feed feed = props.feed();

        Map<Long, Double> order = new HashMap<>();
        for (long id : candidates) {
            if (!excluded.test(id)) order.put(id, scores.score(id) + feed.swipeNoise() * SeededNoise.of(seed, id));
        }
        List<Long> main = sortDesc(order);
        if (feed.explorationEvery() <= 0 || profile.positiveSignals() == 0) return main;

        TitleFeatureIndex index = scores.index();
        double strongest = main.stream().mapToDouble(id -> genreWeight(profile, index, id)).max().orElse(0);
        Map<Long, Double> exploration = new HashMap<>();
        for (long id : main) {
            if (genreWeight(profile, index, id) <= feed.explorationGenreShare() * strongest) {
                double quality = index.get(id).map(TitleFeatureIndex.Entry::quality).orElse(0.0);
                exploration.put(id, quality + feed.swipeNoise() * SeededNoise.of(seed + ":x", id));
            }
        }
        return interleave(main, sortDesc(exploration), feed.explorationEvery());
    }

    /** {@code 0.6·score + 0.4·popularidade}; os curtidos vão para o fim (na mesma ordem). */
    public List<Long> reelsOrder(long userId, List<Long> candidates) {
        UserScores scores = recommendations.scores(userId);
        LongPredicate excluded = recommendations.excluded(scores.profile(), REELS_EXCLUSIONS);
        RecommendationProperties.Feed feed = props.feed();
        Map<Long, Double> order = new HashMap<>();
        for (long id : candidates) {
            if (excluded.test(id)) continue;
            double pop = scores.index().get(id).map(TitleFeatureIndex.Entry::popularity).orElse(0.0);
            order.put(id, feed.reelsScore() * scores.score(id) + feed.reelsPopularity() * pop);
        }
        List<Long> sorted = sortDesc(order);
        Set<Long> liked = scores.profile().liked();
        List<Long> out = new ArrayList<>(sorted.size());
        sorted.stream().filter(id -> !liked.contains(id)).forEach(out::add);
        sorted.stream().filter(liked::contains).forEach(out::add);
        return out;
    }

    /** Cada {@code every}-ésima posição vem de {@code exploration}; o resto segue {@code main}, sem repetir. */
    static List<Long> interleave(List<Long> main, List<Long> exploration, int every) {
        List<Long> out = new ArrayList<>(main.size());
        Set<Long> used = new HashSet<>();
        Iterator<Long> m = main.iterator();
        Iterator<Long> x = exploration.iterator();
        while (out.size() < main.size()) {
            Long next = null;
            if ((out.size() + 1) % every == 0) next = nextUnused(x, used);
            if (next == null) next = nextUnused(m, used);
            if (next == null) next = nextUnused(x, used);
            if (next == null) break;
            used.add(next);
            out.add(next);
        }
        return out;
    }

    private static Long nextUnused(Iterator<Long> it, Set<Long> used) {
        while (it.hasNext()) {
            Long id = it.next();
            if (!used.contains(id)) return id;
        }
        return null;
    }

    private static double genreWeight(TasteProfile profile, TitleFeatureIndex index, long id) {
        Long g = index.get(id).map(TitleFeatureIndex.Entry::primaryGenreId).orElse(null);
        return g == null ? 0 : profile.genreWeight(g);
    }

    private static List<Long> sortDesc(Map<Long, Double> values) {
        List<Long> ids = new ArrayList<>(values.keySet());
        ids.sort(Comparator.comparingDouble((Long id) -> -values.get(id)).thenComparingLong(id -> id));
        return ids;
    }
}

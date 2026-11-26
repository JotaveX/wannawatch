package br.com.jotave.wannawatch.recommendation.domain;

import br.com.jotave.wannawatch.recommendation.domain.TitleFeatureIndex.Entry;
import java.util.Map;

/**
 * Motivo de um título recomendado para um usuário. Vale a primeira regra que se aplica:
 *
 * <ol>
 *   <li>um título que a pessoa curtiu ou salvou é parecido o bastante → "Porque você curtiu X"
 *       (o mais parecido);</li>
 *   <li>a feature de pessoa ou de gênero que mais contribui ({@code positivo[f] · t[f]}) é uma
 *       pessoa → "De {nome}" (direção, criação, roteiro) ou "Com {nome}" (elenco);</li>
 *   <li>é um gênero → "Você curte {gênero}";</li>
 *   <li>senão → "Em alta" ou "Bem avaliado", o que pesar mais no título.</li>
 * </ol>
 *
 * Keywords, idioma, década e tipo também pesam no score, mas não viram texto: "Porque tem a
 * keyword 'space'" não ajuda ninguém.
 */
public final class ReasonResolver {

    private final double similarityThreshold;

    public ReasonResolver(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public Reason resolve(TasteProfile profile, TitleFeatureIndex index, Entry title) {
        Entry best = null;
        double bestSimilarity = similarityThreshold;
        for (long anchorId : profile.anchors()) {
            if (anchorId == title.id()) continue;
            Entry anchor = index.get(anchorId).orElse(null);
            if (anchor == null) continue;
            double s = index.similarity(anchor, title);
            if (s >= bestSimilarity && (best == null || s > bestSimilarity)) {
                best = anchor;
                bestSimilarity = s;
            }
        }
        if (best != null) return new Reason(ReasonKind.BECAUSE_LIKED, "Porque você curtiu " + best.features().title());

        String topKey = null;
        double topContribution = 0;
        for (Map.Entry<String, Double> f : title.vector().asMap().entrySet()) {
            String key = f.getKey();
            if (!key.startsWith("p:") && !key.startsWith("g:")) continue;
            double c = profile.positive().get(key) * f.getValue();
            if (c > topContribution) {
                topContribution = c;
                topKey = key;
            }
        }
        if (topKey != null) {
            long id = Long.parseLong(topKey.substring(2));
            if (topKey.startsWith("p:")) {
                String name = index.personName(id);
                CreditRole role = title.roles().get(id);
                if (name != null && role != null && role != CreditRole.OTHER_CREW) {
                    return new Reason(ReasonKind.PERSON, (role == CreditRole.CAST ? "Com " : "De ") + name);
                }
            } else {
                String name = index.genreName(id);
                if (name != null) return new Reason(ReasonKind.GENRE, "Você curte " + lowerFirst(name));
            }
        }
        return popular(title);
    }

    /** "Ficção científica" → "ficção científica" (o gênero no meio da frase). */
    public static String lowerFirst(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toLowerCase(java.util.Locale.forLanguageTag("pt-BR")) + s.substring(1);
    }

    public static Reason popular(Entry title) {
        return title.popularity() >= title.quality()
                ? new Reason(ReasonKind.POPULAR, "Em alta")
                : new Reason(ReasonKind.POPULAR, "Bem avaliado");
    }
}

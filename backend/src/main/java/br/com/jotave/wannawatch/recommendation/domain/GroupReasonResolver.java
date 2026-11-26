package br.com.jotave.wannawatch.recommendation.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.text.Collator;
import java.util.Locale;
import java.util.Map;

/**
 * Motivo de um título no deck do Match, do ponto de vista de quem pede o deck ("você"). Vale a
 * primeira regra que se aplica:
 *
 * <ol>
 *   <li>todos com percentil alto → "Bom pra você e pra Bia" · "Bom pra você, Bia e Caio";</li>
 *   <li>alguém da sala quer ver → "Bia quer ver" · "Você e Caio querem ver";</li>
 *   <li>o gênero principal pesa bastante no perfil de todos → "Todo mundo curte comédia";</li>
 *   <li>senão, sem motivo.</li>
 * </ol>
 * Os outros nomes saem em ordem alfabética, com "você" sempre primeiro.
 */
public final class GroupReasonResolver {

    private static final Collator PT = Collator.getInstance(Locale.forLanguageTag("pt-BR"));

    private final GroupParams params;

    public GroupReasonResolver(GroupParams params) {
        this.params = params;
    }

    /** Participante da sala: id, nome de exibição e as notas dele. */
    public record Member(long userId, String displayName, UserScores scores) {}

    public Reason resolve(long me, List<Member> members, TitleFeatureIndex.Entry title) {
        if (members.isEmpty()) return null;
        long id = title.id();

        if (members.size() >= 2 && members.stream().allMatch(m -> m.scores().percentile(id) >= params.goodForAll())) {
            List<String> others = othersSorted(me, members);
            return new Reason(ReasonKind.GROUP, "Bom pra você" + (others.size() == 1 ? " e pra " + others.getFirst()
                    : ", " + join(others)));
        }

        List<Member> wanting = members.stream().filter(m -> m.scores().profile().liked().contains(id)).toList();
        if (!wanting.isEmpty()) {
            boolean mine = wanting.stream().anyMatch(m -> m.userId() == me);
            List<String> names = new ArrayList<>();
            if (mine) names.add("Você");
            names.addAll(othersSorted(me, wanting));
            return new Reason(ReasonKind.WANTED_BY, join(names) + (names.size() == 1 ? " quer ver" : " querem ver"));
        }

        Long genre = title.primaryGenreId();
        if (genre != null && members.size() >= 2 && members.stream().allMatch(m -> strongGenre(m.scores().profile(), genre))) {
            String name = members.getFirst().scores().index().genreName(genre);
            if (name != null) return new Reason(ReasonKind.GENRE, "Todo mundo curte " + ReasonResolver.lowerFirst(name));
        }
        return null;
    }

    private boolean strongGenre(TasteProfile profile, long genreId) {
        double weight = profile.genreWeight(genreId);
        if (weight <= 0) return false;
        double strongest = profile.positive().asMap().entrySet().stream()
                .filter(e -> e.getKey().startsWith("g:")).mapToDouble(Map.Entry::getValue).max().orElse(0);
        return weight >= params.sharedGenreShare() * strongest;
    }

    private static List<String> othersSorted(long me, List<Member> members) {
        return members.stream().filter(m -> m.userId() != me).map(Member::displayName)
                .sorted(Comparator.comparing(s -> s, PT)).toList();
    }

    /** "A" · "A e B" · "A, B e C" */
    static String join(List<String> names) {
        if (names.size() <= 1) return names.isEmpty() ? "" : names.getFirst();
        return String.join(", ", names.subList(0, names.size() - 1)) + " e " + names.getLast();
    }
}

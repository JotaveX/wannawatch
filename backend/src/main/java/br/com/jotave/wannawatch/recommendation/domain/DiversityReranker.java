package br.com.jotave.wannawatch.recommendation.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;

/**
 * Evita um topo monótono: entre os {@code window} primeiros, no máximo {@code maxPerGenre} com o
 * mesmo gênero principal. Guloso: pega o próximo da fila que não estoura o limite; se nenhum
 * couber, segue a ordem original. Depois da janela, a ordem não muda.
 */
public final class DiversityReranker {

    private DiversityReranker() {}

    public static List<Long> rerank(List<Long> ranked, LongFunction<Long> primaryGenre, int window, int maxPerGenre) {
        if (window <= 0 || maxPerGenre <= 0 || ranked.size() <= maxPerGenre) return ranked;
        LinkedList<Long> queue = new LinkedList<>(ranked);
        List<Long> out = new ArrayList<>(ranked.size());
        Map<Long, Integer> used = new HashMap<>();
        while (out.size() < window && !queue.isEmpty()) {
            Long pick = null;
            for (Long id : queue) {
                Long g = primaryGenre.apply(id);
                if (g == null || used.getOrDefault(g, 0) < maxPerGenre) {
                    pick = id;
                    break;
                }
            }
            if (pick == null) pick = queue.getFirst();
            queue.remove(pick);
            Long g = primaryGenre.apply(pick);
            if (g != null) used.merge(g, 1, Integer::sum);
            out.add(pick);
        }
        out.addAll(queue);
        return out;
    }
}

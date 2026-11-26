package br.com.jotave.wannawatch.recommendation.domain;

/**
 * Agregação do grupo no Match ("average without misery").
 *
 * @param lambda           quanto o desvio-padrão dos percentis desconta (grupo dividido = pior)
 * @param miseryThreshold  se o percentil de alguém ficar abaixo disso, o título vai para o fim
 * @param likeBonus        por participante que tem o título no "quero ver"
 * @param seenPenalty      por participante que já viu
 * @param noise            peso do ruído md5(seed:id), para salas diferentes variarem um pouco
 * @param goodForAll       percentil mínimo de todos para "Bom pra você e pra Bia"
 * @param sharedGenreShare "Todo mundo curte X": X pesa pelo menos isso do gênero mais forte de cada um
 */
public record GroupParams(double lambda, double miseryThreshold, double likeBonus, double seenPenalty,
                          double noise, double goodForAll, double sharedGenreShare) {

    public static GroupParams defaults() {
        return new GroupParams(0.5, 0.25, 0.15, 0.20, 0.05, 0.6, 0.5);
    }
}

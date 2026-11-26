package br.com.jotave.wannawatch.recommendation.domain;

/** Como uma pessoa entra no vetor do título. A ordem é a prioridade quando ela tem mais de um papel. */
public enum CreditRole {
    /** Direção (filme) ou criação (série). */
    DIRECTION,
    /** Roteiro, história, obra original. */
    WRITING,
    CAST,
    /** Trilha sonora e fotografia. */
    OTHER_CREW;

    /** Papel a partir do job guardado em title_credit (em inglês, como vem da TMDB); nulo = não usado. */
    public static CreditRole of(String creditType, String job) {
        if ("CAST".equals(creditType)) return CAST;
        if (job == null) return null;
        return switch (job) {
            case "Director", "Creator" -> DIRECTION;
            case "Writer", "Screenplay", "Story", "Novel" -> WRITING;
            case "Original Music Composer", "Composer", "Director of Photography" -> OTHER_CREW;
            default -> null;
        };
    }
}

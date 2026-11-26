package br.com.jotave.wannawatch.recommendation.domain;

/** De onde veio o motivo da recomendação. */
public enum ReasonKind {
    /** "Porque você curtiu Dark" */
    BECAUSE_LIKED,
    /** "De Christopher Nolan" · "Com Pedro Pascal" */
    PERSON,
    /** "Você curte Ficção científica" · "Todo mundo curte Comédia" */
    GENRE,
    /** "Bom pra você e pra Bia" */
    GROUP,
    /** "Bia quer ver" */
    WANTED_BY,
    /** "Em alta" · "Bem avaliado" */
    POPULAR
}

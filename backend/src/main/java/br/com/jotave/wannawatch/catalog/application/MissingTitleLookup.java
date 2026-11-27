package br.com.jotave.wannawatch.catalog.application;

/** Procura fora daqui (na TMDB) títulos que a busca não achou e os traz para o catálogo. */
public interface MissingTitleLookup {

    /** Devolve true se algum título novo foi gravado (vale buscar de novo). */
    boolean fetchMissing(String query);
}

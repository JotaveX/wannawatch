package br.com.jotave.wannawatch.recommendation.domain;

/** O que pode tirar um título de um feed personalizado. */
public enum Exclusion {
    LIKED, SAVED, SEEN, PASSED,
    /** "talvez" mais recente que o tempo de espera configurado */
    RECENT_MAYBE
}

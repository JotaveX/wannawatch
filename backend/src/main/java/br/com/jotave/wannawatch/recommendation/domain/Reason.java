package br.com.jotave.wannawatch.recommendation.domain;

/** Por que um título foi recomendado; {@code text} já vem pronto para a tela, em pt-BR. */
public record Reason(ReasonKind kind, String text) {}

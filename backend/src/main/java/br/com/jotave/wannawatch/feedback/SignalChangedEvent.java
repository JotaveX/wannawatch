package br.com.jotave.wannawatch.feedback;

/** Publicado em toda escrita de sinal, curtida ou voto de um usuário (o gosto dele mudou). */
public record SignalChangedEvent(long userId) {}

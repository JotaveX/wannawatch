package br.com.jotave.wannawatch.recommendation.domain;

import java.time.Instant;

/** Uma interação de um usuário com um título, com a data (para o decaimento). */
public record Interaction(long titleId, InteractionType type, Instant at) {}

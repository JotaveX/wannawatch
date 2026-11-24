package br.com.jotave.wannawatch.shared.web;

import java.util.List;

/** Paginação por cursor. {@code nextCursor} nulo = acabou. */
public record CursorResponse<T>(List<T> items, String nextCursor) {}

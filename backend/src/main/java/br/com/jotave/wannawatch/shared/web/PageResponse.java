package br.com.jotave.wannawatch.shared.web;

import java.util.List;

/** Paginação por página (0 é a primeira). */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalItems) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalItems / size);
        return new PageResponse<>(items, page, size, totalItems, totalPages);
    }
}

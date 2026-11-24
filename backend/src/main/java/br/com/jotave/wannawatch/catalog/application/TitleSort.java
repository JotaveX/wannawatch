package br.com.jotave.wannawatch.catalog.application;

public enum TitleSort {
    POPULARITY("t.popularity DESC, t.id DESC"),
    RATING("t.vote_average DESC NULLS LAST, t.vote_count DESC NULLS LAST, t.id DESC"),
    RELEASE("t.release_date DESC NULLS LAST, t.id DESC");

    final String orderBy;

    TitleSort(String orderBy) {
        this.orderBy = orderBy;
    }
}

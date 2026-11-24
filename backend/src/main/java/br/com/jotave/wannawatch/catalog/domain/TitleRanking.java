package br.com.jotave.wannawatch.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

/** Posição de um título numa lista da TMDB (ex.: trending do dia). */
@Entity
@Table(name = "title_ranking")
public class TitleRanking {

    @EmbeddedId
    private Key id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "title_id")
    private Title title;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    protected TitleRanking() {}

    public TitleRanking(String listKey, int position, Title title, Instant syncedAt) {
        this.id = new Key(listKey, position);
        this.title = title;
        this.syncedAt = syncedAt;
    }

    public Key getId() { return id; }
    public Title getTitle() { return title; }
    public Instant getSyncedAt() { return syncedAt; }

    @Embeddable
    public record Key(
            @Column(name = "list_key") String listKey,
            @Column(name = "position") int position) implements Serializable {}
}

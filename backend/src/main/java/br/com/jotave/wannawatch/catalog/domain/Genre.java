package br.com.jotave.wannawatch.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "genre")
public class Genre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, unique = true)
    private Integer externalId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "for_movie", nullable = false)
    private boolean forMovie;

    @Column(name = "for_series", nullable = false)
    private boolean forSeries;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Genre() {}

    public Genre(Integer externalId) {
        this.externalId = externalId;
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Integer getExternalId() { return externalId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public boolean isForMovie() { return forMovie; }
    public void setForMovie(boolean forMovie) { this.forMovie = forMovie; }
    public boolean isForSeries() { return forSeries; }
    public void setForSeries(boolean forSeries) { this.forSeries = forSeries; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

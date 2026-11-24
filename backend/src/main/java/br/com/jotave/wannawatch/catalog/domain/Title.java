package br.com.jotave.wannawatch.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Um filme ou uma série. Colunas específicas de cada tipo são opcionais.
 * Os gêneros (tabela title_genre, com a ordem da TMDB) são gravados pelo importador e lidos por SQL.
 */
@Entity
@Table(name = "title")
public class Title {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false)
    private Integer externalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false)
    private MediaType mediaType;

    @Column(nullable = false)
    private String title;

    @Column(name = "original_title")
    private String originalTitle;

    private String tagline;

    private String overview;

    @Column(name = "original_language")
    private String originalLanguage;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "runtime_minutes")
    private Integer runtimeMinutes;

    @Column(name = "seasons_count")
    private Integer seasonsCount;

    @Column(name = "episodes_count")
    private Integer episodesCount;

    private String status;

    @Column(name = "poster_path")
    private String posterPath;

    @Column(name = "backdrop_path")
    private String backdropPath;

    @Column(name = "vote_average")
    private Double voteAverage;

    @Column(name = "vote_count")
    private Integer voteCount;

    @Column(nullable = false)
    private double popularity;

    @Column(name = "age_rating")
    private String ageRating;

    private Long budget;

    private Long revenue;

    @Column(name = "trailer_youtube_key")
    private String trailerYoutubeKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private TitleDetails details = TitleDetails.empty();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    protected Title() {}

    public Title(Integer externalId, MediaType mediaType) {
        this.externalId = externalId;
        this.mediaType = mediaType;
    }

    public void markSynced(Instant when) {
        this.lastSyncedAt = when;
        this.updatedAt = when;
    }

    public Long getId() { return id; }
    public Integer getExternalId() { return externalId; }
    public MediaType getMediaType() { return mediaType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getOriginalTitle() { return originalTitle; }
    public void setOriginalTitle(String originalTitle) { this.originalTitle = originalTitle; }
    public String getTagline() { return tagline; }
    public void setTagline(String tagline) { this.tagline = tagline; }
    public String getOverview() { return overview; }
    public void setOverview(String overview) { this.overview = overview; }
    public String getOriginalLanguage() { return originalLanguage; }
    public void setOriginalLanguage(String originalLanguage) { this.originalLanguage = originalLanguage; }
    public LocalDate getReleaseDate() { return releaseDate; }
    public void setReleaseDate(LocalDate releaseDate) { this.releaseDate = releaseDate; }
    public Integer getRuntimeMinutes() { return runtimeMinutes; }
    public void setRuntimeMinutes(Integer runtimeMinutes) { this.runtimeMinutes = runtimeMinutes; }
    public Integer getSeasonsCount() { return seasonsCount; }
    public void setSeasonsCount(Integer seasonsCount) { this.seasonsCount = seasonsCount; }
    public Integer getEpisodesCount() { return episodesCount; }
    public void setEpisodesCount(Integer episodesCount) { this.episodesCount = episodesCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPosterPath() { return posterPath; }
    public void setPosterPath(String posterPath) { this.posterPath = posterPath; }
    public String getBackdropPath() { return backdropPath; }
    public void setBackdropPath(String backdropPath) { this.backdropPath = backdropPath; }
    public Double getVoteAverage() { return voteAverage; }
    public void setVoteAverage(Double voteAverage) { this.voteAverage = voteAverage; }
    public Integer getVoteCount() { return voteCount; }
    public void setVoteCount(Integer voteCount) { this.voteCount = voteCount; }
    public double getPopularity() { return popularity; }
    public void setPopularity(double popularity) { this.popularity = popularity; }
    public String getAgeRating() { return ageRating; }
    public void setAgeRating(String ageRating) { this.ageRating = ageRating; }
    public Long getBudget() { return budget; }
    public void setBudget(Long budget) { this.budget = budget; }
    public Long getRevenue() { return revenue; }
    public void setRevenue(Long revenue) { this.revenue = revenue; }
    public String getTrailerYoutubeKey() { return trailerYoutubeKey; }
    public void setTrailerYoutubeKey(String trailerYoutubeKey) { this.trailerYoutubeKey = trailerYoutubeKey; }
    public TitleDetails getDetails() { return details; }
    public void setDetails(TitleDetails details) { this.details = details; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastSyncedAt() { return lastSyncedAt; }
}

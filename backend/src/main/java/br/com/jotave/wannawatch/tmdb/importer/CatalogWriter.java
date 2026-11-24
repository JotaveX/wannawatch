package br.com.jotave.wannawatch.tmdb.importer;

import br.com.jotave.wannawatch.catalog.domain.Genre;
import br.com.jotave.wannawatch.catalog.domain.GenreRepository;
import br.com.jotave.wannawatch.catalog.domain.Person;
import br.com.jotave.wannawatch.catalog.domain.PersonRepository;
import br.com.jotave.wannawatch.catalog.domain.Title;
import br.com.jotave.wannawatch.catalog.domain.TitleCredit;
import br.com.jotave.wannawatch.catalog.domain.TitleCreditRepository;
import br.com.jotave.wannawatch.catalog.domain.TitleRanking;
import br.com.jotave.wannawatch.catalog.domain.TitleRankingRepository;
import br.com.jotave.wannawatch.catalog.domain.TitleRepository;
import br.com.jotave.wannawatch.tmdb.client.TmdbDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Grava no banco o que veio da TMDB. Cada método é uma transação. */
@Service
public class CatalogWriter {

    private final GenreRepository genres;
    private final TitleRepository titles;
    private final PersonRepository people;
    private final TitleCreditRepository credits;
    private final TitleRankingRepository rankings;

    public CatalogWriter(GenreRepository genres, TitleRepository titles, PersonRepository people,
                         TitleCreditRepository credits, TitleRankingRepository rankings) {
        this.genres = genres;
        this.titles = titles;
        this.people = people;
        this.credits = credits;
        this.rankings = rankings;
    }

    /**
     * Junta as listas de gêneros de filme e de série. Um id que aparece nas duas vira uma linha
     * só, com as duas flags. O nome da lista de filmes tem preferência.
     */
    @Transactional
    public int upsertGenres(TmdbDto.GenreList movie, TmdbDto.GenreList tv) {
        record Merged(String name, boolean forMovie, boolean forSeries) {}
        Map<Integer, Merged> merged = new LinkedHashMap<>();
        for (TmdbDto.Genre g : movie.genres()) {
            merged.put(g.id(), new Merged(g.name(), true, false));
        }
        for (TmdbDto.Genre g : tv.genres()) {
            merged.merge(g.id(), new Merged(g.name(), false, true),
                    (a, b) -> new Merged(a.name(), a.forMovie(), true));
        }

        Map<Integer, Genre> existing = genres.findByExternalIdIn(merged.keySet()).stream()
                .collect(Collectors.toMap(Genre::getExternalId, Function.identity()));
        Set<String> usedSlugs = new HashSet<>();
        genres.findAll().stream()
                .filter(g -> !merged.containsKey(g.getExternalId()))
                .forEach(g -> usedSlugs.add(g.getSlug()));

        List<Genre> toSave = new ArrayList<>();
        merged.forEach((extId, m) -> {
            Genre g = existing.getOrDefault(extId, new Genre(extId));
            String slug = TmdbMapper.slug(m.name());
            if (!usedSlugs.add(slug)) {
                slug = slug + "-" + extId;
                usedSlugs.add(slug);
            }
            g.setName(m.name().trim());
            g.setSlug(slug);
            g.setForMovie(m.forMovie());
            g.setForSeries(m.forSeries());
            g.touch();
            toSave.add(g);
        });
        genres.saveAll(toSave);
        return toSave.size();
    }

    /** Cria ou atualiza um título (pela dupla external_id + tipo) com gêneros e créditos. */
    @Transactional
    public Long upsertTitle(ImportedTitle in, Instant now) {
        Title t = titles.findByExternalIdAndMediaType(in.externalId(), in.mediaType())
                .orElseGet(() -> new Title(in.externalId(), in.mediaType()));
        t.setTitle(in.title());
        t.setOriginalTitle(in.originalTitle());
        t.setTagline(in.tagline());
        t.setOverview(in.overview());
        t.setOriginalLanguage(in.originalLanguage());
        t.setReleaseDate(in.releaseDate());
        t.setRuntimeMinutes(in.runtimeMinutes());
        t.setSeasonsCount(in.seasonsCount());
        t.setEpisodesCount(in.episodesCount());
        t.setStatus(in.status());
        t.setPosterPath(in.posterPath());
        t.setBackdropPath(in.backdropPath());
        t.setVoteAverage(in.voteAverage());
        t.setVoteCount(in.voteCount());
        t.setPopularity(in.popularity());
        t.setAgeRating(in.ageRating());
        t.setBudget(in.budget());
        t.setRevenue(in.revenue());
        t.setTrailerYoutubeKey(in.trailerYoutubeKey());
        t.setDetails(in.details());
        t.replaceGenres(new HashSet<>(genres.findByExternalIdIn(in.genreExternalIds())));
        t.markSynced(now);
        t = titles.save(t);

        credits.deleteByTitleId(t.getId());
        Map<Integer, Person> persons = upsertPeople(in.credits());
        List<TitleCredit> rows = new ArrayList<>();
        for (ImportedTitle.Credit c : in.credits()) {
            rows.add(new TitleCredit(t, persons.get(c.personExternalId()), c.type(),
                    c.character(), c.job(), c.department(), c.order()));
        }
        credits.saveAll(rows);
        return t.getId();
    }

    /** Troca o conteúdo de uma lista (ex.: trending do dia) pela ordem nova. */
    @Transactional
    public void replaceRanking(String listKey, List<Long> titleIds, Instant now) {
        rankings.deleteByListKey(listKey);
        rankings.flush();
        List<TitleRanking> rows = new ArrayList<>();
        for (int i = 0; i < titleIds.size(); i++) {
            rows.add(new TitleRanking(listKey, i + 1, titles.getReferenceById(titleIds.get(i)), now));
        }
        rankings.saveAll(rows);
    }

    private Map<Integer, Person> upsertPeople(List<ImportedTitle.Credit> list) {
        Map<Integer, ImportedTitle.Credit> byId = new HashMap<>();
        list.forEach(c -> byId.putIfAbsent(c.personExternalId(), c));
        Map<Integer, Person> found = people.findByExternalIdIn(byId.keySet()).stream()
                .collect(Collectors.toMap(Person::getExternalId, Function.identity()));
        byId.forEach((extId, c) -> {
            Person p = found.computeIfAbsent(extId, Person::new);
            p.update(c.name(), c.profilePath(), c.knownForDepartment());
        });
        people.saveAll(found.values());
        return found;
    }
}

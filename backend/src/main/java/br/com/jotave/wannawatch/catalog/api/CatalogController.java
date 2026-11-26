package br.com.jotave.wannawatch.catalog.api;

import br.com.jotave.wannawatch.auth.AuthUser;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.GenreResponse;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.HomeResponse;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.SearchHit;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.TitleCard;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.TitleDetail;
import br.com.jotave.wannawatch.catalog.application.FeedService;
import br.com.jotave.wannawatch.catalog.application.HomeService;
import br.com.jotave.wannawatch.catalog.application.SearchService;
import br.com.jotave.wannawatch.catalog.application.TitleQueryService;
import br.com.jotave.wannawatch.catalog.application.TitleSort;
import br.com.jotave.wannawatch.catalog.domain.MediaType;
import br.com.jotave.wannawatch.shared.web.CursorResponse;
import br.com.jotave.wannawatch.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** As validações dos parâmetros (@Min, @Max...) são feitas pelo Spring MVC e viram 400 em ProblemDetail. */
@RestController
@RequestMapping("/api")
@Tag(name = "Catálogo")
public class CatalogController {

    private final TitleQueryService titles;
    private final FeedService feeds;
    private final HomeService home;
    private final SearchService search;

    public CatalogController(TitleQueryService titles, FeedService feeds, HomeService home, SearchService search) {
        this.titles = titles;
        this.feeds = feeds;
        this.home = home;
        this.search = search;
    }

    @GetMapping("/genres")
    @Operation(summary = "Lista os gêneros, com quantos títulos cada um tem")
    public List<GenreResponse> genres(@RequestParam(required = false) MediaType type) {
        return titles.genres(type);
    }

    @GetMapping("/titles")
    @Operation(summary = "Lista títulos paginados, com filtros por tipo e gênero")
    public PageResponse<TitleCard> titles(
            @RequestParam(required = false) MediaType type,
            @Parameter(description = "slug do gênero, ex.: ficcao-cientifica") @RequestParam(required = false) String genre,
            @RequestParam(defaultValue = "POPULARITY") TitleSort sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return titles.titles(type, genre, sort, page, size);
    }

    @GetMapping("/titles/{id}")
    @Operation(summary = "Detalhe completo de um título")
    public TitleDetail title(@PathVariable long id) {
        return titles.detail(id);
    }

    @GetMapping("/home")
    @Operation(summary = "Destaque e fileiras da Home (personalizadas com login)")
    public HomeResponse home(@Parameter(hidden = true) Optional<AuthUser> user) {
        return home.home(user);
    }

    @GetMapping("/swipe/deck")
    @Operation(summary = "Lote de títulos para o Swipe: personalizado com login, aleatório fixo por seed sem")
    public CursorResponse<TitleCard> swipeDeck(
            @Parameter(hidden = true) Optional<AuthUser> user,
            @Parameter(description = "gerado pelo front a cada sessão") @RequestParam
            @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String seed,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @RequestParam(required = false) MediaType type,
            @RequestParam(required = false) String genre) {
        return feeds.swipeDeck(user, seed, cursor, size, type, genre);
    }

    @GetMapping("/reels")
    @Operation(summary = "Feed do Reels, paginado por cursor: personalizado com login, por popularidade sem")
    public CursorResponse<TitleCard> reels(
            @Parameter(hidden = true) Optional<AuthUser> user,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(required = false) MediaType type,
            @RequestParam(required = false) String genre) {
        return feeds.reels(user, cursor, size, type, genre);
    }

    @GetMapping("/search")
    @Operation(summary = "Busca fuzzy por título, elenco/equipe e gênero")
    public PageResponse<SearchHit> search(
            @RequestParam @Size(min = 2, max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return search.search(q, page, size);
    }
}

package br.com.jotave.wannawatch.match;

import br.com.jotave.wannawatch.auth.AuthUser;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.TitleCard;
import br.com.jotave.wannawatch.match.MatchDtos.CreateRoomRequest;
import br.com.jotave.wannawatch.match.MatchDtos.RoomResponse;
import br.com.jotave.wannawatch.match.MatchDtos.VoteRequest;
import br.com.jotave.wannawatch.shared.web.CursorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Swipe em conjunto. O front consulta {@code GET /rooms/{code}} a cada poucos segundos para ver
 * quem entrou, se o dono começou e se já deu match. Tudo aqui exige login.
 */
@RestController
@RequestMapping("/api/match/rooms")
@Tag(name = "Match")
public class MatchController {

    private final MatchService match;

    public MatchController(MatchService match) {
        this.match = match;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria uma sala (quem cria já entra nela)")
    public RoomResponse create(@Parameter(hidden = true) AuthUser user, @Valid @RequestBody CreateRoomRequest body) {
        return match.create(user, body.mediaType(), body.genre());
    }

    @GetMapping
    @Operation(summary = "Salas abertas: esperando gente (de todos) e as que estou votando; mais novas primeiro")
    public List<RoomResponse> open(@Parameter(hidden = true) AuthUser user) {
        return match.openRooms(user);
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Fecha a sala (só o dono, enquanto ela espera)")
    public void close(@Parameter(hidden = true) AuthUser user, @PathVariable String code) {
        match.close(user, code);
    }

    @PostMapping("/{code}/start")
    @Operation(summary = "Começa a votação (só o dono, com pelo menos 2 pessoas)")
    public RoomResponse start(@Parameter(hidden = true) AuthUser user, @PathVariable String code) {
        return match.start(user, code);
    }

    @GetMapping("/{code}")
    @Operation(summary = "Estado da sala: participantes, progresso e o título vencedor")
    public RoomResponse room(@Parameter(hidden = true) AuthUser user, @PathVariable String code) {
        return match.room(user, code);
    }

    @PostMapping("/{code}/join")
    @Operation(summary = "Entra na sala pelo código (idempotente; gente nova só enquanto a sala espera)")
    public RoomResponse join(@Parameter(hidden = true) AuthUser user, @PathVariable String code) {
        return match.join(user, code);
    }

    @DeleteMapping("/{code}/participants/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Sai da sala e apaga os próprios votos")
    public void leave(@Parameter(hidden = true) AuthUser user, @PathVariable String code) {
        match.leave(user, code);
    }

    @GetMapping("/{code}/deck")
    @Operation(summary = "Deck da sala, sem os títulos que eu já votei")
    public CursorResponse<TitleCard> deck(
            @Parameter(hidden = true) AuthUser user,
            @PathVariable String code,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return match.deck(user, code, cursor, size);
    }

    @PutMapping("/{code}/votes/{titleId}")
    @Operation(summary = "Vota num título (quero ver ou passa) e devolve a sala atualizada")
    public RoomResponse vote(@Parameter(hidden = true) AuthUser user, @PathVariable String code,
                             @PathVariable long titleId, @Valid @RequestBody VoteRequest body) {
        return match.vote(user, code, titleId, body.liked());
    }

    @DeleteMapping("/{code}/votes/{titleId}")
    @Operation(summary = "Desfaz um voto")
    public RoomResponse unvote(@Parameter(hidden = true) AuthUser user, @PathVariable String code,
                               @PathVariable long titleId) {
        return match.unvote(user, code, titleId);
    }
}

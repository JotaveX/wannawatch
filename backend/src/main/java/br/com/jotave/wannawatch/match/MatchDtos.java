package br.com.jotave.wannawatch.match;

import br.com.jotave.wannawatch.auth.UserRef;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.GenreRef;
import br.com.jotave.wannawatch.catalog.api.CatalogDtos.TitleCard;
import br.com.jotave.wannawatch.catalog.domain.MediaType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Contratos JSON das salas de Match. */
public final class MatchDtos {

    private MatchDtos() {}

    /** WAITING: esperando a galera (dá pra entrar); VOTING: votando; MATCHED: deu match. */
    public enum RoomStatus { WAITING, VOTING, MATCHED }

    /** Os dois filtros são opcionais: sem eles o deck tem filmes e séries de todos os gêneros. */
    public record CreateRoomRequest(MediaType mediaType, @Size(max = 120) String genre) {}

    public record VoteRequest(@NotNull Boolean liked) {}

    public record Participant(UserRef user, int votes, int likes, Instant joinedAt) {}

    public record RoomResponse(
            String code,
            MediaType mediaType,
            GenreRef genre,
            UserRef owner,
            List<Participant> participants,
            RoomStatus status,
            /** índice do mascote sorteado para a sala (a lista de imagens fica no front) */
            int mascot,
            /** o título em que todos deram "quero ver"; nulo enquanto a sala está aberta */
            TitleCard winner,
            /** quando o dono começou a votação; nulo enquanto espera a galera */
            Instant startedAt,
            Instant matchedAt,
            Instant createdAt) {}
}

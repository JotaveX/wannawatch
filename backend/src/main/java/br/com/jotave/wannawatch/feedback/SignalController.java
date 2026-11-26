package br.com.jotave.wannawatch.feedback;

import br.com.jotave.wannawatch.auth.AuthUser;
import br.com.jotave.wannawatch.feedback.SignalService.TitleSignals;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Passa / talvez / já vi / salvar. Tudo aqui exige login. */
@RestController
@RequestMapping("/api")
@Tag(name = "Sinais")
public class SignalController {

    private final SignalService signals;

    public SignalController(SignalService signals) {
        this.signals = signals;
    }

    @PutMapping("/titles/{id}/signals/{kind}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Marca passa, talvez, já vi ou salvar (idempotente)")
    public void put(@Parameter(hidden = true) AuthUser user, @PathVariable long id,
                    @Parameter(description = "PASS, MAYBE, SEEN ou SAVED") @PathVariable String kind,
                    @Parameter(description = "SWIPE, REELS, DETAILS, HOME ou MATCH") @RequestParam(required = false) String source) {
        signals.put(user, id, SignalKind.parse(kind), InteractionSource.parse(source));
    }

    @DeleteMapping("/titles/{id}/signals/{kind}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desfaz o sinal (idempotente)")
    public void remove(@Parameter(hidden = true) AuthUser user, @PathVariable long id, @PathVariable String kind,
                       @RequestParam(required = false) String source) {
        signals.remove(user, id, SignalKind.parse(kind), InteractionSource.parse(source));
    }

    @GetMapping("/me/signals")
    @Operation(summary = "Meus sinais para um lote de títulos")
    public List<TitleSignals> forTitles(@Parameter(hidden = true) AuthUser user,
                                        @RequestParam @Size(min = 1, max = 100) List<Long> ids) {
        return signals.forTitles(user, ids);
    }
}

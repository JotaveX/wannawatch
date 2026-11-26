package br.com.jotave.wannawatch.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Em que etapa do cadastro a conta está. As contas de teste já vêm com tudo feito.
 *
 * @param email         e-mail da conta
 * @param emailVerified já confirmou o e-mail com o código
 * @param titlesPicked  já escolheu os títulos de que gosta (pelo menos 5)
 * @param admin         é administrador (vê a revisão dos vídeos do Reels)
 */
public record AccountStatus(String email, boolean emailVerified, boolean titlesPicked, boolean admin) {

    /** Terminou o cadastro: pode usar o app inteiro. */
    @JsonProperty
    public boolean complete() {
        return emailVerified && titlesPicked;
    }
}

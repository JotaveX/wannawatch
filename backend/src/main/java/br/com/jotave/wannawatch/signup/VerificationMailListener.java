package br.com.jotave.wannawatch.signup;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Manda o e-mail só depois do commit: se a transação desfizer, ninguém recebe um código que não
 * existe; e se o SMTP demorar, não segura conexão nem trava a linha do usuário.
 */
@Component
class VerificationMailListener {

    private final VerificationMailer mailer;

    VerificationMailListener(VerificationMailer mailer) {
        this.mailer = mailer;
    }

    @TransactionalEventListener
    void onIssued(VerificationCodeIssued issued) {
        mailer.send(issued.email(), issued.displayName(), issued.code());
    }
}

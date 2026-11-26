package br.com.jotave.wannawatch.signup;

/** Manda o código de confirmação de e-mail. Nos testes é trocado por um mock. */
public interface VerificationMailer {

    void send(String email, String displayName, String code);
}

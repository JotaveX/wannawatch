package br.com.jotave.wannawatch.signup;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Manda o código pela API do Resend ({@code POST /emails}). O remetente ({@code MAIL_FROM}) precisa
 * ser de um domínio verificado no Resend; com {@code onboarding@resend.dev} só chega no e-mail do
 * dono da conta.
 *
 * <p>Como no SMTP, falha só vai para o log: a pessoa pode pedir outro código.
 */
class ResendVerificationMailer implements VerificationMailer {

    private static final Logger log = LoggerFactory.getLogger(ResendVerificationMailer.class);

    private final RestClient http;
    private final String from;

    ResendVerificationMailer(RestClient.Builder builder, String baseUrl, String apiKey, String from) {
        this.http = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
        this.from = from;
    }

    @Override
    public void send(String email, String displayName, String code) {
        VerificationMail mail = VerificationMail.of(displayName, code);
        try {
            http.post()
                    .uri("/emails")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "from", from,
                            "to", List.of(email),
                            "subject", mail.subject(),
                            "text", mail.text(),
                            "html", mail.html()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Não deu para mandar o código de confirmação para {} pelo Resend: {}", email, e.getMessage());
        }
    }
}

package br.com.jotave.wannawatch.signup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Manda o código por SMTP ({@code spring.mail.*}). Em desenvolvimento o SMTP é o Mailpit do
 * docker-compose (caixa de entrada em http://localhost:8025).
 *
 * <p>Se o envio falhar, só registra no log: a conta já foi criada e a pessoa pode pedir outro
 * código na tela de confirmação.
 */
@Component
class SmtpVerificationMailer implements VerificationMailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpVerificationMailer.class);

    private final JavaMailSender sender;
    private final String from;

    SmtpVerificationMailer(JavaMailSender sender, @Value("${wannawatch.mail.from}") String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void send(String email, String displayName, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Seu código do wannawatch?: " + code);
        message.setText("""
                Oi, %s!

                Seu código para confirmar o e-mail no wannawatch? é:

                    %s

                Ele vale por %d minutos. Se não foi você que criou a conta, é só ignorar este e-mail.

                — wannawatch?
                """.formatted(displayName, code, EmailVerificationService.CODE_TTL.toMinutes()));
        try {
            sender.send(message);
        } catch (MailException e) {
            log.warn("Não deu para mandar o código de confirmação para {}: {}", email, e.getMessage());
        }
    }
}

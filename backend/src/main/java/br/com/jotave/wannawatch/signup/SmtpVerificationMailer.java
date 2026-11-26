package br.com.jotave.wannawatch.signup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Manda o código por SMTP ({@code spring.mail.*}). É o padrão em desenvolvimento, com o Mailpit
 * do docker-compose (caixa de entrada em http://localhost:8025).
 *
 * <p>Se o envio falhar, só registra no log: a conta já foi criada e a pessoa pode pedir outro
 * código na tela de confirmação.
 */
class SmtpVerificationMailer implements VerificationMailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpVerificationMailer.class);

    private final JavaMailSender sender;
    private final String from;

    SmtpVerificationMailer(JavaMailSender sender, String from) {
        this.sender = sender;
        this.from = from;
    }

    @Override
    public void send(String email, String displayName, String code) {
        VerificationMail mail = VerificationMail.of(displayName, code);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject(mail.subject());
        message.setText(mail.text());
        try {
            sender.send(message);
        } catch (MailException e) {
            log.warn("Não deu para mandar o código de confirmação para {}: {}", email, e.getMessage());
        }
    }
}

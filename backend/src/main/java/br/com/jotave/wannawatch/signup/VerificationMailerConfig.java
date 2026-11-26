package br.com.jotave.wannawatch.signup;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestClient;

/** Com {@code RESEND_API_KEY} preenchido o código vai pelo Resend; sem ela, pelo SMTP (Mailpit). */
@Configuration
class VerificationMailerConfig {

    @Bean
    VerificationMailer verificationMailer(
            @Value("${wannawatch.mail.from}") String from,
            @Value("${wannawatch.mail.resend.api-key:}") String resendApiKey,
            @Value("${wannawatch.mail.resend.base-url:https://api.resend.com}") String resendBaseUrl,
            ObjectProvider<RestClient.Builder> restClient,
            ObjectProvider<JavaMailSender> smtp) {
        if (!resendApiKey.isBlank()) {
            return new ResendVerificationMailer(restClient.getObject(), resendBaseUrl, resendApiKey, from);
        }
        return new SmtpVerificationMailer(smtp.getObject(), from);
    }
}

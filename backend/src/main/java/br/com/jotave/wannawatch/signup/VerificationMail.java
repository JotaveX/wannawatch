package br.com.jotave.wannawatch.signup;

import org.springframework.web.util.HtmlUtils;

/** Assunto e corpo (texto e HTML) do e-mail com o código, iguais em qualquer forma de envio. */
record VerificationMail(String subject, String text, String html) {

    static VerificationMail of(String displayName, String code) {
        long minutes = EmailVerificationService.CODE_TTL.toMinutes();
        String subject = "Seu código do wannawatch?: " + code;
        String text = """
                Oi, %s!

                Seu código para confirmar o e-mail no wannawatch? é:

                    %s

                Ele vale por %d minutos. Se não foi você que criou a conta, é só ignorar este e-mail.

                — wannawatch?
                """.formatted(displayName, code, minutes);
        String html = """
                <div style="font-family:Arial,Helvetica,sans-serif;max-width:480px;margin:0 auto;padding:24px;color:#1a1a1a">
                  <p style="font-size:16px;margin:0 0 16px">Oi, %s!</p>
                  <p style="font-size:16px;margin:0 0 16px">Seu código para confirmar o e-mail no <strong>wannawatch?</strong> é:</p>
                  <p style="font-size:32px;font-weight:700;letter-spacing:8px;margin:0 0 16px;font-family:'Courier New',monospace">%s</p>
                  <p style="font-size:14px;color:#555;margin:0">Ele vale por %d minutos. Se não foi você que criou a conta, é só ignorar este e-mail.</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(displayName), code, minutes);
        return new VerificationMail(subject, text, html);
    }
}

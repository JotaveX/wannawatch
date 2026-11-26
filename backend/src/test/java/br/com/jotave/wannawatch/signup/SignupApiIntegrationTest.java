package br.com.jotave.wannawatch.signup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.jotave.wannawatch.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Cadastro em etapas (conta → títulos → e-mail) contra um Postgres real, com o e-mail num mock. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SignupApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @MockitoBean VerificationMailer mailer;

    final List<Long> titles = new ArrayList<>();

    @BeforeEach
    void seed() {
        cleanup();
        jdbc.sql("TRUNCATE title, genre, person RESTART IDENTITY CASCADE").update();
        titles.clear();
        for (int i = 1; i <= 6; i++) {
            titles.add(jdbc.sql("""
                    INSERT INTO title (external_id, media_type, title, overview, popularity)
                    VALUES (:e, 'MOVIE', :t, 'Sinopse.', 10) RETURNING id""")
                    .param("e", i).param("t", "Filme " + i).query(Long.class).single());
        }
    }

    /** As contas criadas aqui não podem vazar para os outros testes (todo mundo é amigo de todo mundo). */
    @AfterEach
    void cleanup() {
        jdbc.sql("DELETE FROM interaction_event WHERE user_id IN (SELECT id FROM app_user WHERE username NOT IN ('jotave', 'bia', 'caio'))").update();
        jdbc.sql("DELETE FROM app_user WHERE username NOT IN ('jotave', 'bia', 'caio')").update();
    }

    @Nested
    class Conta {

        @Test
        void criaJaLogadaEMandaOCodigo() throws Exception {
            register("Ana Lima", " Ana.Lima ", "Ana@Exemplo.com", "senha-boa")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.token").isString())
                    .andExpect(jsonPath("$.user.username").value("ana.lima"))
                    .andExpect(jsonPath("$.user.displayName").value("Ana Lima"))
                    .andExpect(jsonPath("$.account.email").value("ana@exemplo.com"))
                    .andExpect(jsonPath("$.account.emailVerified").value(false))
                    .andExpect(jsonPath("$.account.titlesPicked").value(false))
                    .andExpect(jsonPath("$.account.complete").value(false));
            String code = lastCode("ana@exemplo.com");
            assertThat(code).matches("\\d{6}");
        }

        @Test
        void entraPorUsuarioOuEmail() throws Exception {
            register("Ana", "ana", "ana@exemplo.com", "senha-boa").andExpect(status().isCreated());
            for (String login : List.of("ana", "ANA@exemplo.com")) {
                mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"" + login + "\",\"password\":\"senha-boa\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.user.username").value("ana"))
                        .andExpect(jsonPath("$.account.complete").value(false));
            }
        }

        @Test
        void contasDeTesteJaVemComTudoFeito() throws Exception {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"bia\",\"password\":\"wannawatch\"}"))
                    .andExpect(jsonPath("$.account.emailVerified").value(true))
                    .andExpect(jsonPath("$.account.titlesPicked").value(true));
        }

        @Test
        void recusaUsuarioOuEmailRepetido() throws Exception {
            register("Ana", "ana", "ana@exemplo.com", "senha-boa").andExpect(status().isCreated());
            register("Outra", "Ana", "outra@exemplo.com", "senha-boa")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("Esse usuário já existe. Tente outro."));
            register("Outra", "outra", "ANA@exemplo.com", "senha-boa")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("Já existe uma conta com esse e-mail."));
            register("Bia 2", "bia", "bia2@exemplo.com", "senha-boa").andExpect(status().isConflict());
        }

        @Test
        void recusaDadosInvalidos() throws Exception {
            register("  ", "ana", "ana@exemplo.com", "senha-boa").andExpect(status().isBadRequest());
            register("Ana", "an", "ana@exemplo.com", "senha-boa").andExpect(status().isBadRequest());
            register("Ana", "ana lima", "ana@exemplo.com", "senha-boa").andExpect(status().isBadRequest());
            register("Ana", "ana", "ana-sem-arroba", "senha-boa").andExpect(status().isBadRequest());
            register("Ana", "ana", "ana@exemplo.com", "curta")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("A senha precisa de pelo menos 8 caracteres."));
            verify(mailer, never()).send(anyString(), anyString(), anyString());
        }
    }

    @Nested
    class Titulos {

        @Test
        void viramQueroVerEOFavoritoVaiProPerfil() throws Exception {
            String token = token(register("Ana", "ana", "ana@exemplo.com", "senha-boa"));
            pick(token, titles.subList(0, 5), titles.get(2))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.titlesPicked").value(true))
                    .andExpect(jsonPath("$.emailVerified").value(false));

            mvc.perform(get("/api/me/profile").header("Authorization", "Bearer " + token))
                    .andExpect(jsonPath("$.favoriteTitle.id").value(titles.get(2)));
            mvc.perform(get("/api/likes").param("titleIds", ids(titles)).header("Authorization", "Bearer " + token))
                    .andExpect(jsonPath("$[?(@.likedByMe == true)].titleId").value(
                            org.hamcrest.Matchers.containsInAnyOrder(titles.subList(0, 5).stream().map(Long::intValue).toArray())));
            assertThat(jdbc.sql("SELECT count(*) FROM interaction_event WHERE source = 'ONBOARDING'")
                    .query(Long.class).single()).isEqualTo(5);
        }

        @Test
        void exigeCincoEFavoritoEntreEles() throws Exception {
            String token = token(register("Ana", "ana", "ana@exemplo.com", "senha-boa"));
            pick(token, titles.subList(0, 4), null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Escolha pelo menos 5 títulos."));
            // repetido não conta duas vezes
            pick(token, List.of(titles.get(0), titles.get(0), titles.get(1), titles.get(2), titles.get(3)), null)
                    .andExpect(status().isBadRequest());
            pick(token, titles.subList(0, 5), titles.get(5))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("O favorito precisa estar entre os escolhidos."));
            pick(token, List.of(titles.get(0), titles.get(1), titles.get(2), titles.get(3), 999_999L), null)
                    .andExpect(status().isBadRequest());
            // sem favorito tudo bem
            pick(token, titles.subList(0, 5), null).andExpect(status().isOk());
        }

        @Test
        void exigeLogin() throws Exception {
            mvc.perform(put("/api/me/onboarding/titles").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"titleIds\":[" + ids(titles) + "]}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class Email {

        @Test
        void codigoCertoConfirma() throws Exception {
            String token = token(register("Ana", "ana", "ana@exemplo.com", "senha-boa"));
            String code = lastCode("ana@exemplo.com");
            verifyCode(token, code.substring(0, 3) + " " + code.substring(3))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.emailVerified").value(true));
            mvc.perform(get("/api/auth/account").header("Authorization", "Bearer " + token))
                    .andExpect(jsonPath("$.user.username").value("ana"))
                    .andExpect(jsonPath("$.account.emailVerified").value(true));
            // confirmar de novo não dá erro
            verifyCode(token, "000000").andExpect(status().isOk());
        }

        @Test
        void tentativasErradasFicamContadas() throws Exception {
            String token = token(register("Ana", "ana", "ana@exemplo.com", "senha-boa"));
            String code = lastCode("ana@exemplo.com");
            String wrong = code.equals("111111") ? "222222" : "111111";
            for (int i = 0; i < EmailVerificationService.MAX_ATTEMPTS; i++) {
                verifyCode(token, wrong).andExpect(status().isBadRequest());
            }
            // nem o certo passa depois de 5 erros: precisa de outro código
            verifyCode(token, code)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Muitas tentativas erradas. Peça um novo código."));
        }

        @Test
        void reenviarSubstituiOCodigoComIntervalo() throws Exception {
            String token = token(register("Ana", "ana", "ana@exemplo.com", "senha-boa"));
            String first = lastCode("ana@exemplo.com");
            resend(token).andExpect(status().isBadRequest());

            jdbc.sql("UPDATE email_verification SET sent_at = sent_at - interval '2 minutes'").update();
            clearInvocations(mailer);
            resend(token).andExpect(status().isNoContent());
            String second = lastCode("ana@exemplo.com");
            if (!first.equals(second)) verifyCode(token, first).andExpect(status().isBadRequest());
            verifyCode(token, second).andExpect(status().isOk());
            resend(token).andExpect(status().isConflict());
        }

        @Test
        void codigoVencido() throws Exception {
            String token = token(register("Ana", "ana", "ana@exemplo.com", "senha-boa"));
            String code = lastCode("ana@exemplo.com");
            jdbc.sql("UPDATE email_verification SET expires_at = now() - interval '1 minute'").update();
            verifyCode(token, code)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Esse código venceu. Peça um novo."));
        }
    }

    private ResultActions register(String name, String username, String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"displayName":"%s","username":"%s","email":"%s","password":"%s"}"""
                        .formatted(name, username, email, password)));
    }

    private ResultActions pick(String token, List<Long> ids, Long favorite) throws Exception {
        return mvc.perform(put("/api/me/onboarding/titles").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titleIds\":[" + ids(ids) + "],\"favoriteTitleId\":" + favorite + "}"));
    }

    private ResultActions verifyCode(String token, String code) throws Exception {
        return mvc.perform(post("/api/auth/email/verify").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"));
    }

    private ResultActions resend(String token) throws Exception {
        return mvc.perform(post("/api/auth/email/resend").header("Authorization", "Bearer " + token));
    }

    private String lastCode(String email) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailer, org.mockito.Mockito.atLeastOnce()).send(eq(email), anyString(), code.capture());
        return code.getValue();
    }

    private static String token(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.token");
    }

    private static String ids(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}

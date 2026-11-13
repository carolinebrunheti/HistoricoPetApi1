package com.documento.HistoricoApi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Seguranca com a aplicacao real: o historico tem dados de TODOS os tutores,
 * entao so a equipe da clinica pode ler. Tutor e token invalido ficam de fora.
 * Mesma configuracao do HistoricoApiApplicationTests: o Spring reaproveita o contexto e os conteineres.
 */
@SpringBootTest(properties = "jwt.secret=" + HistoricoApiApplicationTests.SEGREDO)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SegurancaIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = { "ADMIN", "ATENDENTE", "VETERINARIO" })
    void equipeDaClinicaLeOHistorico(String role) throws Exception {
        mockMvc.perform(get("/historico").header("Authorization", "Bearer " + token(role, Instant.now().plusSeconds(300))))
                .andExpect(status().isOk());
    }

    @Test
    void tutorNaoLeOHistoricoDeTodos() throws Exception {
        mockMvc.perform(get("/historico").header("Authorization", "Bearer " + token("TUTOR", Instant.now().plusSeconds(300))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/historico/1").header("Authorization", "Bearer " + token("TUTOR", Instant.now().plusSeconds(300))))
                .andExpect(status().isForbidden());
    }

    @Test
    void semTokenDa401() throws Exception {
        mockMvc.perform(get("/historico")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenExpiradoDa401() throws Exception {
        mockMvc.perform(get("/historico").header("Authorization", "Bearer " + token("ADMIN", Instant.now().minusSeconds(120))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSemRoleDa403() throws Exception {
        mockMvc.perform(get("/historico").header("Authorization", "Bearer " + token(null, Instant.now().plusSeconds(300))))
                .andExpect(status().isForbidden());
    }

    private String token(String role, Instant expiraEm) {
        var chave = new SecretKeySpec(HistoricoApiApplicationTests.SEGREDO.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var claims = JwtClaimsSet.builder()
                .subject("teste@petservices.dev")
                .issuedAt(expiraEm.minusSeconds(600))
                .expiresAt(expiraEm);
        if (role != null) {
            claims.claim("role", role);
        }
        return NimbusJwtEncoder.withSecretKey(chave).build()
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }
}

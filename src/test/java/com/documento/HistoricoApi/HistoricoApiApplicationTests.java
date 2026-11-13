package com.documento.HistoricoApi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.documento.HistoricoApi.enums.StatusConsulta;
import com.documento.HistoricoApi.eventos.AgendarConsultaEvent;
import com.documento.HistoricoApi.eventos.TipoEvento;
import com.documento.HistoricoApi.repositories.HistoricoConsultaRepository;

/**
 * Teste de integracao: a aplicacao inteira sobe contra Postgres e Kafka reais (Testcontainers).
 * Prova o que os testes com mock nao provam: as migrations do Flyway rodam, o Hibernate valida
 * as tabelas (ddl-auto=validate), o consumidor le o JSON do Kafka e grava no banco, e a API
 * aceita um token JWT assinado de verdade.
 */
@SpringBootTest(properties = "jwt.secret=" + HistoricoApiApplicationTests.SEGREDO)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HistoricoApiApplicationTests {

    static final String SEGREDO = "chave-de-teste-com-no-minimo-32-caracteres";
    private static final String TOPICO = "agendar_consulta";

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private HistoricoConsultaRepository historicoRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
        // subir o contexto ja prova que o Flyway criou a tabela e o Hibernate a validou
        assertThat(historicoRepository.count()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void eventoCriadoNoKafkaViraHistoricoNoBancoEApareceNaApi() throws Exception {
        enviar(evento(TipoEvento.CRIADO, 1001));

        // o consumidor roda em outra thread: espera o registro aparecer no banco
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(historicoRepository.findById(1001)).isPresent());

        mockMvc.perform(get("/historico/1001").header("Authorization", "Bearer " + token("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consultaId").value(1001))
                .andExpect(jsonPath("$.tutorNome").value("Maria"))
                .andExpect(jsonPath("$.petNome").value("Rex"))
                .andExpect(jsonPath("$.status").value("AGENDADA"));
    }

    @Test
    void cancelamentoAtualizaAMesmaConsultaEmVezDeCriarOutra() {
        enviar(evento(TipoEvento.CRIADO, 1002));
        enviar(evento(TipoEvento.CANCELADA, 1002));

        // mesma chave (consultaId) -> mesma particao -> os dois eventos chegam na ordem
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(historicoRepository.findById(1002))
                        .hasValueSatisfying(h -> assertThat(h.getStatus()).isEqualTo(StatusConsulta.CANCELADA)));
    }

    @Test
    void tokenAssinadoComOutroSegredoERecusado() throws Exception {
        String tokenFalso = token("ADMIN", "outro-segredo-qualquer-com-mais-de-32-caracteres");

        mockMvc.perform(get("/historico").header("Authorization", "Bearer " + tokenFalso))
                .andExpect(status().isUnauthorized());
    }

    private void enviar(AgendarConsultaEvent evento) {
        // .get(): so segue depois que o Kafka confirmou a gravacao
        try {
            kafkaTemplate.send(TOPICO, String.valueOf(evento.consultaId()), evento).get();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao publicar no Kafka de teste", e);
        }
    }

    private AgendarConsultaEvent evento(TipoEvento tipo, int consultaId) {
        return new AgendarConsultaEvent(tipo, consultaId, "Maria", "maria@teste.dev", "Rex", "Dr. João",
                LocalDateTime.of(2026, 12, 1, 10, 0), LocalDateTime.now());
    }

    // gera um token igual ao do petServices: HS256 com a claim "role"
    private String token(String role) {
        return token(role, SEGREDO);
    }

    private String token(String role, String segredo) {
        var chave = new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = NimbusJwtEncoder.withSecretKey(chave).build();
        var claims = JwtClaimsSet.builder()
                .subject("teste@petservices.dev")
                .claim("role", role)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}

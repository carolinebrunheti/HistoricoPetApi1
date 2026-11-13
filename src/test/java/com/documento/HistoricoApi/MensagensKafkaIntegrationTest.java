package com.documento.HistoricoApi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.testcontainers.kafka.KafkaContainer;

import com.documento.HistoricoApi.enums.StatusConsulta;
import com.documento.HistoricoApi.repositories.HistoricoConsultaRepository;

/**
 * Mensagens problematicas no topico agendar_consulta, contra Kafka e Postgres reais:
 * - DLT: mensagem que nao da para processar sai do caminho (vai para agendar_consulta-dlt
 *   com o conteudo original) e o consumidor continua lendo as proximas;
 * - idempotencia: o Kafka entrega "pelo menos uma vez", entao o mesmo evento pode chegar
 *   repetido ou atrasado, e isso nao pode duplicar nem desfazer dados.
 * Mesma configuracao do HistoricoApiApplicationTests: o Spring reaproveita o contexto e os conteineres.
 */
@SpringBootTest(properties = "jwt.secret=" + HistoricoApiApplicationTests.SEGREDO)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MensagensKafkaIntegrationTest {

    private static final String TOPICO = "agendar_consulta";
    private static final String DLT = "agendar_consulta-dlt";

    @Autowired private KafkaContainer kafka;
    @Autowired private HistoricoConsultaRepository historicoRepository;

    @Test
    void mensagemQueNaoEJsonVaiParaDltComConteudoOriginalEOConsumidorSegue() {
        enviarBruto("2001", "isto nao e json");
        enviarBruto("2002", evento("CRIADO", 2002, LocalDateTime.of(2026, 11, 1, 9, 0)));

        // a mensagem valida que veio depois foi processada: a ruim nao travou a particao
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(historicoRepository.findById(2002)).isPresent());

        ConsumerRecord<String, byte[]> naDlt = lerDlt("2001");
        assertThat(new String(naDlt.value(), StandardCharsets.UTF_8)).isEqualTo("isto nao e json");
        assertThat(cabecalho(naDlt, "kafka_dlt-exception-fqcn")).contains("DeserializationException");
    }

    @Test
    void eventoQueFalhaAoGravarTentaDeNovoEDepoisVaiParaDlt() {
        // JSON valido, mas sem consultaId: o save falha sempre (erro "permanente")
        String semId = evento("CRIADO", 2003, LocalDateTime.of(2026, 11, 1, 9, 0))
                .replace("\"consultaId\":2003", "\"consultaId\":null");
        enviarBruto("2003", semId);

        // 1 tentativa + 3 retries com 2 s de intervalo => chega na DLT em ~6 s
        // aqui o JSON ja tinha sido convertido em objeto, entao a DLT recebe o objeto
        // serializado de novo (mesmos dados, formatacao pode mudar)
        ConsumerRecord<String, byte[]> naDlt = lerDlt("2003");
        assertThat(new String(naDlt.value(), StandardCharsets.UTF_8))
                .contains("\"consultaId\":null")
                .contains("\"tutorNome\":\"Maria\"");
        assertThat(cabecalho(naDlt, "kafka_dlt-exception-message")).isNotBlank();
    }

    @Test
    void eventoRepetidoNaoDuplicaNemAlteraOHistorico() {
        String criado = evento("CRIADO", 2004, LocalDateTime.of(2026, 11, 1, 9, 0));
        enviarBruto("2004", criado);
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(historicoRepository.findById(2004)).isPresent());
        long antes = historicoRepository.count();

        enviarBruto("2004", criado); // reentrega
        esperarProcessar("2004", 2005);

        assertThat(historicoRepository.count()).isEqualTo(antes + 1); // +1 = so o marcador 2005
        assertThat(historicoRepository.findById(2004).orElseThrow().getStatus()).isEqualTo(StatusConsulta.AGENDADA);
    }

    @Test
    void eventoAntigoReentregueNaoDesfazOCancelamento() {
        String criado = evento("CRIADO", 2006, LocalDateTime.of(2026, 11, 1, 9, 0));
        enviarBruto("2006", criado);
        enviarBruto("2006", evento("CANCELADA", 2006, LocalDateTime.of(2026, 11, 1, 10, 0)));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(historicoRepository.findById(2006))
                        .hasValueSatisfying(h -> assertThat(h.getStatus()).isEqualTo(StatusConsulta.CANCELADA)));

        // o CRIADO das 9h chega de novo (ex.: o consumidor caiu antes de confirmar o offset)
        enviarBruto("2006", criado);
        esperarProcessar("2006", 2007);

        assertThat(historicoRepository.findById(2006).orElseThrow().getStatus())
                .as("um evento mais antigo nao pode sobrescrever um mais novo")
                .isEqualTo(StatusConsulta.CANCELADA);
    }

    // manda um marcador com a MESMA chave (mesma particao, entao e lido depois da mensagem
    // anterior) e espera ele ser gravado: assim sabemos que a anterior ja foi processada
    private void esperarProcessar(String chave, int consultaIdMarcador) {
        enviarBruto(chave, evento("CRIADO", consultaIdMarcador, LocalDateTime.of(2026, 11, 1, 9, 0)));
        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(historicoRepository.findById(consultaIdMarcador)).isPresent());
    }

    private String evento(String tipo, int consultaId, LocalDateTime ocorridoEm) {
        return """
                {"tipoEvento":"%s","consultaId":%d,"tutorNome":"Maria","tutorEmail":"maria@teste.dev",\
                "petNome":"Rex","veterinarioNome":"Dr. João","diaHora":"2026-12-01T10:00:00","ocorridoEm":"%s"}"""
                .formatted(tipo, consultaId, ocorridoEm);
    }

    // envia os bytes exatamente como estao, sem passar pelo serializador JSON da aplicacao
    private void enviarBruto(String chave, String valor) {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(TOPICO, chave, valor.getBytes(StandardCharsets.UTF_8))).get();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao publicar no Kafka de teste", e);
        }
    }

    private ConsumerRecord<String, byte[]> lerDlt(String chave) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "teste-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(DLT));
            long limite = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < limite) {
                for (ConsumerRecord<String, byte[]> r : consumer.poll(Duration.ofMillis(500))) {
                    if (chave.equals(r.key())) {
                        return r;
                    }
                }
            }
        }
        throw new AssertionError("Mensagem " + chave + " nao apareceu na " + DLT);
    }

    private String cabecalho(ConsumerRecord<?, ?> r, String nome) {
        Header h = r.headers().lastHeader(nome);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }
}

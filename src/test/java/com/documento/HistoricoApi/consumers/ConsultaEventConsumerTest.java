package com.documento.HistoricoApi.consumers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.documento.HistoricoApi.entities.HistoricoConsultaEntity;
import com.documento.HistoricoApi.enums.StatusConsulta;
import com.documento.HistoricoApi.eventos.AgendarConsultaEvent;
import com.documento.HistoricoApi.eventos.TipoEvento;
import com.documento.HistoricoApi.repositories.HistoricoConsultaRepository;

@ExtendWith(MockitoExtension.class)
class ConsultaEventConsumerTest {

    @Mock
    private HistoricoConsultaRepository historicoConsultaRepository;

    @InjectMocks
    private ConsultaEventConsumer consumer;

    @Captor
    private ArgumentCaptor<HistoricoConsultaEntity> historicoCaptor;

    private static final LocalDateTime DIA_HORA = LocalDateTime.of(2026, 10, 10, 10, 0);
    private static final LocalDateTime OCORRIDO_EM = LocalDateTime.of(2026, 10, 1, 9, 0);

    private AgendarConsultaEvent criarEvento(TipoEvento tipoEvento) {
        return new AgendarConsultaEvent(tipoEvento, 1, "Maria", "maria@email.com",
                "Rex", "Dr. João", DIA_HORA, OCORRIDO_EM);
    }

    @Test
    void consumirDeveSalvarHistoricoComDadosDoEvento() {
        consumer.consumir(criarEvento(TipoEvento.CRIADO));

        verify(historicoConsultaRepository).save(historicoCaptor.capture());
        HistoricoConsultaEntity salvo = historicoCaptor.getValue();

        assertThat(salvo.getConsultaId()).isEqualTo(1);
        assertThat(salvo.getTutorNome()).isEqualTo("Maria");
        assertThat(salvo.getPetNome()).isEqualTo("Rex");
        assertThat(salvo.getVeterinarioNome()).isEqualTo("Dr. João");
        assertThat(salvo.getDiaHora()).isEqualTo(DIA_HORA);
        assertThat(salvo.getAtualizadoEm()).isEqualTo(OCORRIDO_EM);
    }

    @Test
    void consumirDeveSalvarStatusCanceladaQuandoEventoForCancelada() {
        consumer.consumir(criarEvento(TipoEvento.CANCELADA));

        verify(historicoConsultaRepository).save(historicoCaptor.capture());
        assertThat(historicoCaptor.getValue().getStatus()).isEqualTo(StatusConsulta.CANCELADA);
    }

    @ParameterizedTest
    @EnumSource(value = TipoEvento.class, names = "CANCELADA", mode = EnumSource.Mode.EXCLUDE)
    void consumirDeveSalvarStatusAgendadaParaDemaisEventos(TipoEvento tipoEvento) {
        consumer.consumir(criarEvento(tipoEvento));

        verify(historicoConsultaRepository).save(historicoCaptor.capture());
        assertThat(historicoCaptor.getValue().getStatus()).isEqualTo(StatusConsulta.AGENDADA);
    }
}

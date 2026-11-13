package com.documento.HistoricoApi.consumers;

import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import com.documento.HistoricoApi.entities.HistoricoConsultaEntity;
import com.documento.HistoricoApi.enums.StatusConsulta;
import com.documento.HistoricoApi.eventos.AgendarConsultaEvent;
import com.documento.HistoricoApi.repositories.HistoricoConsultaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j 
@Configuration
@RequiredArgsConstructor 
public class ConsultaEventConsumer {


    private final HistoricoConsultaRepository historicoConsultaRepository;

    @KafkaListener(
            topics = "agendar_consulta",
            properties = "spring.json.value.default.type=com.documento.HistoricoApi.eventos.AgendarConsultaEvent"
    )

    public void consumir(AgendarConsultaEvent evento){
        log.info("Recebido {} da consulta {}", evento.tipoEvento(), evento.consultaId());

        // o Kafka entrega "pelo menos uma vez": o mesmo evento pode chegar de novo, inclusive
        // depois de um mais novo (ex.: o consumidor caiu antes de confirmar o offset).
        // So aplica o evento se ele for mais novo do que o que ja esta gravado.
        var atual = historicoConsultaRepository.findById(evento.consultaId());
        if (atual.isPresent() && evento.ocorridoEm() != null
                && !evento.ocorridoEm().isAfter(atual.get().getAtualizadoEm())) {
            log.info("Ignorado {} da consulta {}: ocorrido em {}, mas o historico ja esta em {}",
                    evento.tipoEvento(), evento.consultaId(), evento.ocorridoEm(), atual.get().getAtualizadoEm());
            return;
        }

        StatusConsulta status = switch (evento.tipoEvento()){
            case CANCELADA -> StatusConsulta.CANCELADA;
            default -> StatusConsulta.AGENDADA;
        };

        historicoConsultaRepository.save(new HistoricoConsultaEntity(
            evento.consultaId(), evento.tutorNome(), evento.petNome(), evento.veterinarioNome(), 
            evento.diaHora(), status, evento.ocorridoEm()
        ));
    }
    
}

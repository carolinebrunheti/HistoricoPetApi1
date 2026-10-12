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

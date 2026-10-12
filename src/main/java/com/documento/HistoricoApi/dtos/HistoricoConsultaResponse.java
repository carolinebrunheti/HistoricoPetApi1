package com.documento.HistoricoApi.dtos;

import java.time.LocalDateTime;

import com.documento.HistoricoApi.entities.HistoricoConsultaEntity;
import com.documento.HistoricoApi.enums.StatusConsulta;

public record HistoricoConsultaResponse(
        Integer consultaId,
        String tutorNome,
        String petNome,
        String veterinarioNome,
        LocalDateTime diaHora,
        StatusConsulta status,
        LocalDateTime atualizadoEm
) {
    public static HistoricoConsultaResponse from(HistoricoConsultaEntity h) {
        return new HistoricoConsultaResponse(h.getConsultaId(), h.getTutorNome(), h.getPetNome(),
                h.getVeterinarioNome(), h.getDiaHora(), h.getStatus(), h.getAtualizadoEm());
    }
}
package com.documento.HistoricoApi.eventos;

import java.time.LocalDateTime;

 public record AgendarConsultaEvent(
        TipoEvento tipoEvento,
        Integer consultaId,
        String tutorNome,
        String tutorEmail,
        String petNome,
        String veterinarioNome,
        LocalDateTime diaHora,
        LocalDateTime ocorridoEm
) {
    
}

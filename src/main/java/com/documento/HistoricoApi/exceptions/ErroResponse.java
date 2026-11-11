package com.documento.HistoricoApi.exceptions;

import java.time.LocalDateTime;
import java.util.List;

/** Mesmo formato de erro do PetServices e do Agendamento. */
public record ErroResponse(
        LocalDateTime timestamp,
        int status,
        String erro,
        String mensagem,
        String path,
        List<String> detalhes
) {}

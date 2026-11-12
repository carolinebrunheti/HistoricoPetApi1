package com.documento.HistoricoApi.exceptions;

import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTransientConnectionException;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Trata so os erros de banco: banco fora do ar vira 503 (temporario) em vez de 500.
 * Os outros erros continuam com o tratamento padrao do Spring.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<ErroResponse> erroDeBanco(Exception ex, HttpServletRequest request) {
        if (bancoIndisponivel(ex)) {
            // sem stacktrace: com o banco fora, seriam centenas de linhas iguais por segundo
            log.warn("Banco indisponivel em {}: {}", request.getRequestURI(), causaRaiz(ex));
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header(HttpHeaders.RETRY_AFTER, "5")
                    .body(montar(HttpStatus.SERVICE_UNAVAILABLE,
                            "Servico temporariamente indisponivel. Tente novamente em alguns segundos.", request));
        }
        log.error("Erro de banco em {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(montar(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno no servidor", request));
    }

    /**
     * Banco fora do ar, ou sem conexao livre no pool depois do connection-timeout.
     * Procura na cadeia de causas, porque o Spring/Hibernate embrulham o erro do driver.
     */
    static boolean bancoIndisponivel(Throwable ex) {
        for (Throwable t = ex; t != null; t = (t.getCause() == t ? null : t.getCause())) {
            if (t instanceof DataAccessResourceFailureException
                    || t instanceof CannotCreateTransactionException
                    || t instanceof SQLTransientConnectionException
                    || t instanceof SQLNonTransientConnectionException) {
                return true;
            }
            // SQLState 08xxx = erro de conexao; 57Pxx = servidor desligando/reiniciando
            if (t instanceof SQLException sql && sql.getSQLState() != null
                    && (sql.getSQLState().startsWith("08") || sql.getSQLState().startsWith("57P"))) {
                return true;
            }
        }
        return false;
    }

    private static String causaRaiz(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }

    private ErroResponse montar(HttpStatus status, String mensagem, HttpServletRequest request) {
        return new ErroResponse(LocalDateTime.now(), status.value(), status.getReasonPhrase(),
                mensagem, request.getRequestURI(), List.of());
    }
}

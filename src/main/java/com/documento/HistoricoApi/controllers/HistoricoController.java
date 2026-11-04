package com.documento.HistoricoApi.controllers;

import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.documento.HistoricoApi.dtos.HistoricoConsultaResponse;
import com.documento.HistoricoApi.repositories.HistoricoConsultaRepository;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/historico")
@RequiredArgsConstructor
public class HistoricoController {

    private final HistoricoConsultaRepository historicoRepository;

    // mais recentes primeiro, 10 por pagina
    @GetMapping
    public ResponseEntity<PagedModel<HistoricoConsultaResponse>> listar(
            @PageableDefault(size = 10, sort = "diaHora", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(new PagedModel<>(historicoRepository.findAll(pageable)
                .map(HistoricoConsultaResponse::from)));
    }

    @GetMapping("/{consultaId}")
    public ResponseEntity<HistoricoConsultaResponse> buscar(@PathVariable Integer consultaId) {
        return historicoRepository.findById(consultaId)
                .map(HistoricoConsultaResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ?sort= com um campo que nao existe na entidade
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ProblemDetail> ordenacaoInvalida(PropertyReferenceException ex) {
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Campo de ordenação inválido: " + ex.getPropertyName()));
    }
}

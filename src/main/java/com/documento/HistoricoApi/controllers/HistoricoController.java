package com.documento.HistoricoApi.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
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

    @GetMapping
    public ResponseEntity<List<HistoricoConsultaResponse>> listar() {
        return ResponseEntity.ok(historicoRepository.findAllByOrderByDiaHoraDesc().stream()
                .map(HistoricoConsultaResponse::from)
                .toList());
    }

    @GetMapping("/{consultaId}")
    public ResponseEntity<HistoricoConsultaResponse> buscar(@PathVariable Integer consultaId) {
        return historicoRepository.findById(consultaId)
                .map(HistoricoConsultaResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
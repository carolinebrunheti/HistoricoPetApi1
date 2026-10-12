package com.documento.HistoricoApi.entities;

import java.time.LocalDateTime;

import com.documento.HistoricoApi.enums.StatusConsulta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tb_historico_consulta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class HistoricoConsultaEntity {

    @Id
    private Integer consultaId;

    @Column(nullable = false)
    private String tutorNome;

    @Column(nullable = false)
    private String petNome;

    @Column(nullable = false)
    private String veterinarioNome;

    @Column(nullable = false)
    private LocalDateTime diaHora;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusConsulta status;

    @Column(nullable = false)
    private LocalDateTime atualizadoEm;
}

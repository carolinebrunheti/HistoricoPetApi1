package com.documento.HistoricoApi.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.documento.HistoricoApi.entities.HistoricoConsultaEntity;

@Repository 
public interface HistoricoConsultaRepository extends JpaRepository<HistoricoConsultaEntity, Integer>{

        List<HistoricoConsultaEntity> findAllByOrderByDiaHoraDesc();
}

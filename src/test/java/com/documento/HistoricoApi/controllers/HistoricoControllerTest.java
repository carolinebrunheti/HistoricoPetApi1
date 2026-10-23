package com.documento.HistoricoApi.controllers;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.documento.HistoricoApi.config.security.SecurityConfig;
import com.documento.HistoricoApi.entities.HistoricoConsultaEntity;
import com.documento.HistoricoApi.enums.StatusConsulta;
import com.documento.HistoricoApi.repositories.HistoricoConsultaRepository;

@WebMvcTest(HistoricoController.class)
@Import(SecurityConfig.class) // traz as regras de autorizacao reais + o JwtDecoder para o slice
@TestPropertySource(properties = "jwt.secret=chave-de-teste-com-no-minimo-32-caracteres")
@WithMockUser(roles = "ADMIN") // por padrao os testes rodam como ADMIN (passa em todas as rotas)
class HistoricoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HistoricoConsultaRepository historicoRepository;

    private HistoricoConsultaEntity criarHistorico(Integer consultaId, LocalDateTime diaHora, StatusConsulta status) {
        return new HistoricoConsultaEntity(consultaId, "Maria", "Rex", "Dr. João",
                diaHora, status, LocalDateTime.of(2026, 10, 1, 9, 0));
    }

    @Test
    void listarDeveRetornarHistoricoOrdenadoPorDiaHora() throws Exception {
        when(historicoRepository.findAllByOrderByDiaHoraDesc()).thenReturn(List.of(
                criarHistorico(2, LocalDateTime.of(2026, 10, 20, 14, 30), StatusConsulta.CANCELADA),
                criarHistorico(1, LocalDateTime.of(2026, 10, 10, 10, 0), StatusConsulta.AGENDADA)));

        mockMvc.perform(get("/historico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].consultaId").value(2))
                .andExpect(jsonPath("$[0].status").value("CANCELADA"))
                .andExpect(jsonPath("$[0].diaHora").value("2026-10-20T14:30:00"))
                .andExpect(jsonPath("$[1].consultaId").value(1))
                .andExpect(jsonPath("$[1].tutorNome").value("Maria"))
                .andExpect(jsonPath("$[1].petNome").value("Rex"))
                .andExpect(jsonPath("$[1].veterinarioNome").value("Dr. João"))
                .andExpect(jsonPath("$[1].status").value("AGENDADA"));

        verify(historicoRepository).findAllByOrderByDiaHoraDesc();
    }

    @Test
    void listarDeveRetornarListaVaziaQuandoNaoHaHistorico() throws Exception {
        when(historicoRepository.findAllByOrderByDiaHoraDesc()).thenReturn(List.of());

        mockMvc.perform(get("/historico"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void buscarDeveRetornarHistoricoQuandoExiste() throws Exception {
        when(historicoRepository.findById(1)).thenReturn(Optional.of(
                criarHistorico(1, LocalDateTime.of(2026, 10, 10, 10, 0), StatusConsulta.AGENDADA)));

        mockMvc.perform(get("/historico/{consultaId}", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consultaId").value(1))
                .andExpect(jsonPath("$.tutorNome").value("Maria"))
                .andExpect(jsonPath("$.petNome").value("Rex"))
                .andExpect(jsonPath("$.veterinarioNome").value("Dr. João"))
                .andExpect(jsonPath("$.diaHora").value("2026-10-10T10:00:00"))
                .andExpect(jsonPath("$.status").value("AGENDADA"))
                .andExpect(jsonPath("$.atualizadoEm").value("2026-10-01T09:00:00"));
    }

    @Test
    void buscarDeveRetornar404QuandoNaoExiste() throws Exception {
        when(historicoRepository.findById(99)).thenReturn(Optional.empty());

        mockMvc.perform(get("/historico/{consultaId}", 99))
                .andExpect(status().isNotFound());
    }

    @Test
    void buscarDeveRetornar400QuandoIdNaoForNumerico() throws Exception {
        mockMvc.perform(get("/historico/{consultaId}", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithAnonymousUser
    void deveRetornar401SemToken() throws Exception {
        mockMvc.perform(get("/historico"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "TUTOR")
    void deveRetornar403QuandoTutorAcessaHistorico() throws Exception {
        mockMvc.perform(get("/historico"))
                .andExpect(status().isForbidden());
    }
}

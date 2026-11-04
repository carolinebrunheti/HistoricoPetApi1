package com.documento.HistoricoApi.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.core.TypeInformation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    private Pageable pageableUsado() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(historicoRepository).findAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void listarDeveRetornarPrimeiraPaginaCom10ItensOrdenadaPorDiaHoraDesc() throws Exception {
        Page<HistoricoConsultaEntity> pagina = new PageImpl<>(List.of(
                criarHistorico(2, LocalDateTime.of(2026, 10, 20, 14, 30), StatusConsulta.CANCELADA),
                criarHistorico(1, LocalDateTime.of(2026, 10, 10, 10, 0), StatusConsulta.AGENDADA)),
                PageRequest.of(0, 10), 2);
        when(historicoRepository.findAll(any(Pageable.class))).thenReturn(pagina);

        mockMvc.perform(get("/historico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].consultaId").value(2))
                .andExpect(jsonPath("$.content[0].status").value("CANCELADA"))
                .andExpect(jsonPath("$.content[0].diaHora").value("2026-10-20T14:30:00"))
                .andExpect(jsonPath("$.content[1].consultaId").value(1))
                .andExpect(jsonPath("$.content[1].tutorNome").value("Maria"))
                .andExpect(jsonPath("$.content[1].petNome").value("Rex"))
                .andExpect(jsonPath("$.content[1].veterinarioNome").value("Dr. João"))
                .andExpect(jsonPath("$.content[1].status").value("AGENDADA"))
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.page.totalPages").value(1));

        Pageable usado = pageableUsado();
        assertThat(usado.getPageNumber()).isZero();
        assertThat(usado.getPageSize()).isEqualTo(10);
        assertThat(usado.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "diaHora"));
    }

    @Test
    void listarDeveRetornarPaginaVaziaQuandoNaoHaHistorico() throws Exception {
        when(historicoRepository.findAll(any(Pageable.class))).thenReturn(Page.empty(PageRequest.of(0, 10)));

        mockMvc.perform(get("/historico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void listarDeveRespeitarPaginaETamanhoPedidos() throws Exception {
        when(historicoRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/historico").param("page", "2").param("size", "5"))
                .andExpect(status().isOk());

        Pageable usado = pageableUsado();
        assertThat(usado.getPageNumber()).isEqualTo(2);
        assertThat(usado.getPageSize()).isEqualTo(5);
    }

    @Test
    void listarDeveLimitarTamanhoDaPaginaEm50() throws Exception {
        when(historicoRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/historico").param("size", "1000"))
                .andExpect(status().isOk());

        assertThat(pageableUsado().getPageSize()).isEqualTo(50);
    }

    @Test
    void listarDeveRetornar400QuandoCampoDeOrdenacaoNaoExiste() throws Exception {
        when(historicoRepository.findAll(any(Pageable.class))).thenThrow(new PropertyReferenceException(
                "naoExiste", TypeInformation.of(HistoricoConsultaEntity.class), List.of()));

        mockMvc.perform(get("/historico").param("sort", "naoExiste"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Campo de ordenação inválido: naoExiste"));
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

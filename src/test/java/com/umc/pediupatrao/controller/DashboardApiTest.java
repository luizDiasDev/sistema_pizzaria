package com.umc.pediupatrao.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.dto.DashboardFiltro;
import com.umc.pediupatrao.dto.DashboardResumo;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.DashboardService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {DashboardController.class, DashboardViewController.class})
@Import(SecurityConfig.class)
class DashboardApiTest {

    private static final LocalDate DIA = LocalDate.of(2026, 11, 10);

    @Autowired MockMvc mvc;

    @MockitoBean DashboardService dashboardService;
    @MockitoBean UsuarioDetailsService usuarioDetailsService;

    @Test
    void semLoginResponde401() throws Exception {
        mvc.perform(get("/api/dashboard/resumo")).andExpect(status().isUnauthorized());
        verifyNoInteractions(dashboardService);
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoAcessaODashboard() throws Exception {
        mvc.perform(get("/api/dashboard/resumo")).andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard/vendas-por-dia")).andExpect(status().isForbidden());
        verifyNoInteractions(dashboardService);
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoAbreATelaDoDashboard() throws Exception {
        mvc.perform(get("/dashboard")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerenteAbreATelaDoDashboard() throws Exception {
        mvc.perform(get("/dashboard")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerenteConsultaOResumo() throws Exception {
        DashboardFiltro filtro = new DashboardFiltro(DIA.minusDays(29), DIA, null);
        when(dashboardService.filtro(any(), any(), any())).thenReturn(filtro);
        when(dashboardService.resumo(filtro)).thenReturn(new DashboardResumo(filtro.inicio(), filtro.fim(),
                new BigDecimal("149.70"), 3, 1, new BigDecimal("74.85"), BigDecimal.ZERO));

        mvc.perform(get("/api/dashboard/resumo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faturamento").value(149.70))
                .andExpect(jsonPath("$.totalPedidos").value(3))
                .andExpect(jsonPath("$.inicio").value("2026-10-12"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminConsultaTodosOsEndpoints() throws Exception {
        when(dashboardService.filtro(any(), any(), any())).thenReturn(new DashboardFiltro(DIA, DIA, null));
        when(dashboardService.vendasPorDia(any())).thenReturn(List.of());
        when(dashboardService.pedidosPorStatus(any())).thenReturn(List.of());
        when(dashboardService.produtosMaisVendidos(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());

        mvc.perform(get("/api/dashboard/vendas-por-dia").param("inicio", "2026-11-01").param("fim", "2026-11-10"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dashboard/pedidos-por-status").param("tipoEntrega", "ENTREGA"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/dashboard/produtos-mais-vendidos").param("limite", "3"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void parametroInvalidoResponde400() throws Exception {
        mvc.perform(get("/api/dashboard/resumo").param("inicio", "10/11/2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mvc.perform(get("/api/dashboard/resumo").param("tipoEntrega", "DRONE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void periodoInvertidoResponde400ComMensagem() throws Exception {
        when(dashboardService.filtro(any(), any(), any()))
                .thenThrow(new RegraNegocioException("A data inicial não pode ser maior que a final.", HttpStatus.BAD_REQUEST));
        mvc.perform(get("/api/dashboard/resumo").param("inicio", "2026-11-10").param("fim", "2026-11-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("A data inicial não pode ser maior que a final."));
    }
}

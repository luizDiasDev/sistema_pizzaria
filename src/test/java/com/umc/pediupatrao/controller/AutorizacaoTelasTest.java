package com.umc.pediupatrao.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import com.umc.pediupatrao.service.UsuarioService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {HomeController.class, PedidoViewController.class, AuditoriaViewController.class})
@Import(SecurityConfig.class)
class AutorizacaoTelasTest {

    @Autowired MockMvc mvc;

    @MockitoBean PedidoService pedidoService;
    @MockitoBean UsuarioService usuarioService;
    @MockitoBean ClienteService clienteService;
    @MockitoBean ProdutoService produtoService;
    @MockitoBean AuditoriaService auditoriaService;
    @MockitoBean UsuarioDetailsService usuarioDetailsService;

    @Test
    void semLoginAsTelasRedirecionamParaOLogin() throws Exception {
        mvc.perform(get("/pedidos")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost/login"));
        mvc.perform(get("/usuarios")).andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoAcessaGestaoDeUsuarios() throws Exception {
        mvc.perform(get("/usuarios")).andExpect(status().isForbidden());
        mvc.perform(get("/usuarios/novo")).andExpect(status().isForbidden());
        mvc.perform(post("/usuarios/editar/u-atendente").with(csrf())
                .param("username", "atendente01").param("role", "ADMIN"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/usuarios/salvar").with(csrf())
                .param("username", "novo").param("password", "senhaForte1").param("role", "ADMIN"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(usuarioService);
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerenteNaoAcessaGestaoDeUsuarios() throws Exception {
        mvc.perform(get("/usuarios")).andExpect(status().isForbidden());
        mvc.perform(post("/usuarios/deletar/u1").with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(usuarioService);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminSalvaUsuarioEVoltaParaALista() throws Exception {
        mvc.perform(post("/usuarios/salvar").with(csrf())
                .param("username", "joana").param("password", "senhaForte1").param("role", "GERENTE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/usuarios"));
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerenteCancelaPedidoPeloFormulario() throws Exception {
        mvc.perform(post("/pedidos/p1/cancelar").with(csrf()).param("justificativa", "Cliente desistiu"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pedidos"))
                .andExpect(flash().attribute("sucesso", "Pedido cancelado."));
        verify(pedidoService).cancelar("p1", "Cliente desistiu");
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void violacaoDeRegraVoltaComMensagemDeErro() throws Exception {
        doThrow(new RegraNegocioException("O desconto máximo permitido é de 20% do valor do pedido."))
                .when(pedidoService).aplicarDesconto(eq("p1"), any(BigDecimal.class));

        mvc.perform(post("/pedidos/p1/desconto").with(csrf()).param("percentual", "35"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("erro", "O desconto máximo permitido é de 20% do valor do pedido."));
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void formularioSemCsrfEhRecusado() throws Exception {
        mvc.perform(post("/pedidos/p1/cancelar").param("justificativa", "Cliente desistiu"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(pedidoService);
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoExecutaAcoesDeGerente() throws Exception {
        mvc.perform(post("/pedidos/p1/cancelar").with(csrf()).param("justificativa", "Cliente desistiu"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/pedidos/p1/desconto").with(csrf()).param("percentual", "10"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/pedidos/p1/avancar").with(csrf()).param("status", "EM_PREPARACAO"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(pedidoService);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminNaoExecutaAcoesDeGerenteNemRegistraPedidos() throws Exception {
        mvc.perform(post("/pedidos/p1/cancelar").with(csrf()).param("justificativa", "Cliente desistiu"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/pedidos/p1/desconto").with(csrf()).param("percentual", "10"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/pedidos/novo")).andExpect(status().isForbidden());
        verifyNoInteractions(pedidoService);
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoVeAuditoria() throws Exception {
        mvc.perform(get("/auditoria")).andExpect(status().isForbidden());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminNaoAcessaClientes() throws Exception {
        mvc.perform(get("/clientes")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerenteSoConsultaClientes() throws Exception {
        mvc.perform(get("/clientes/novo")).andExpect(status().isForbidden());
        mvc.perform(post("/clientes/salvar").with(csrf()).param("nome", "A").param("telefone", "1"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/clientes/editar/c1").with(csrf()).param("nome", "A").param("telefone", "1"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(clienteService);
    }
}

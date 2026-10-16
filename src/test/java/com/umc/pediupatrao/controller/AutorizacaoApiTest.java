package com.umc.pediupatrao.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.umc.pediupatrao.config.SecurityConfig;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.UsuarioDetailsService;
import com.umc.pediupatrao.service.UsuarioService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {PedidoController.class, UsuarioController.class,
        ClienteController.class, AuditoriaController.class})
@Import(SecurityConfig.class)
class AutorizacaoApiTest {

    @Autowired MockMvc mvc;

    @MockitoBean PedidoService pedidoService;
    @MockitoBean UsuarioService usuarioService;
    @MockitoBean ClienteService clienteService;
    @MockitoBean AuditoriaService auditoriaService;
    @MockitoBean UsuarioDetailsService usuarioDetailsService;

    private static final String DESCONTO_JSON = "{\"percentual\": 10}";
    private static final String CANCELAR_JSON = "{\"justificativa\": \"Cliente desistiu\"}";
    private static final String PEDIDO_JSON =
            "{\"clienteId\":\"c1\",\"tipoEntrega\":\"ENTREGA\",\"itens\":[{\"produtoId\":\"p1\",\"quantidade\":1}]}";

    @Test
    void semAutenticacaoAsApisRespondem401() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/clientes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auditoria")).andExpect(status().isUnauthorized());
    }

    @Test
    void apiDeUsuariosDeixouDeSerPublica() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/usuarios").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"hacker\",\"password\":\"senhaForte1\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(usuarioService);
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void operacaoDeEscritaSemTokenCsrfEhRecusada() throws Exception {
        mvc.perform(post("/api/pedidos/p1/cancelar").contentType(MediaType.APPLICATION_JSON).content(CANCELAR_JSON))
                .andExpect(status().isForbidden());
        verifyNoInteractions(pedidoService);
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerentePodeAplicarDescontoECancelar() throws Exception {
        when(pedidoService.aplicarDesconto(any(), any())).thenReturn(new Pedido());
        when(pedidoService.cancelar(any(), any())).thenReturn(new Pedido());

        mvc.perform(post("/api/pedidos/p1/desconto").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(DESCONTO_JSON))
                .andExpect(status().isOk());
        mvc.perform(post("/api/pedidos/p1/cancelar").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(CANCELAR_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminNaoPodeAplicarDescontoNemCancelar() throws Exception {
        mvc.perform(post("/api/pedidos/p1/desconto").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(DESCONTO_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        mvc.perform(post("/api/pedidos/p1/cancelar").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(CANCELAR_JSON))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/pedidos/p1/status").with(csrf()).param("status", "PRONTO"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(pedidoService);
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoPodeAplicarDescontoCancelarNemMudarStatus() throws Exception {
        mvc.perform(post("/api/pedidos/p1/desconto").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(DESCONTO_JSON))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/pedidos/p1/cancelar").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(CANCELAR_JSON))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/pedidos/p1/status").with(csrf()).param("status", "EM_PREPARACAO"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(pedidoService);
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteRegistraEConsultaPedidos() throws Exception {
        when(pedidoService.criarPedido(any())).thenReturn(new Pedido());
        when(pedidoService.listarPedidos()).thenReturn(List.of());

        mvc.perform(post("/api/pedidos").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(PEDIDO_JSON))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/pedidos")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminConsultaMasNaoRegistraPedidos() throws Exception {
        when(pedidoService.listarPedidos()).thenReturn(List.of());

        mvc.perform(get("/api/pedidos")).andExpect(status().isOk());
        mvc.perform(post("/api/pedidos").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(PEDIDO_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerenteMudaStatusDePedido() throws Exception {
        when(pedidoService.atualizarStatus(any(), any())).thenReturn(new Pedido());
        mvc.perform(put("/api/pedidos/p1/status").with(csrf()).param("status", "EM_PREPARACAO"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void totalEnviadoNaRequisicaoEhIgnorado() throws Exception {
        when(pedidoService.criarPedido(any())).thenReturn(new Pedido());
        mvc.perform(post("/api/pedidos").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clienteId\":\"c1\",\"total\":0.01,\"status\":\"FINALIZADO\","
                        + "\"itens\":[{\"produtoId\":\"p1\",\"quantidade\":1,\"precoUnitario\":0.01}]}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminAdministraUsuariosESenhaNuncaEhExposta() throws Exception {
        Usuario u = new Usuario();
        u.setId("u1");
        u.setUsername("joana");
        u.setPassword("$2a$10$hashsecreto");
        u.setRole("ATENDENTE");
        when(usuarioService.listarTodos()).thenReturn(List.of(u));

        mvc.perform(get("/api/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("joana"))
                .andExpect(jsonPath("$[0].role").value("ATENDENTE"))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoSeAutoEleva() throws Exception {
        mvc.perform(put("/api/usuarios/u-atendente").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"atendente01\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/usuarios").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"outro\",\"password\":\"senhaForte1\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios")).andExpect(status().isForbidden());
        verifyNoInteractions(usuarioService);
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerenteNaoAdministraUsuarios() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/usuarios/u1").with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(usuarioService);
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteNaoConsultaAuditoria() throws Exception {
        mvc.perform(get("/api/auditoria")).andExpect(status().isForbidden());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminEGerenteConsultamAuditoria() throws Exception {
        when(auditoriaService.listar()).thenReturn(List.of());
        mvc.perform(get("/api/auditoria")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void naoExisteRotaParaAlterarOuExcluirAuditoria() throws Exception {
        mvc.perform(delete("/api/auditoria/abc").with(csrf())).andExpect(status().is4xxClientError());
        mvc.perform(put("/api/auditoria/abc").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(post("/api/auditoria").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminNaoAcessaClientes() throws Exception {
        mvc.perform(get("/api/clientes")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerenteConsultaClientesMasNaoCadastraNemAltera() throws Exception {
        when(clienteService.listarClientes()).thenReturn(List.of());
        mvc.perform(get("/api/clientes")).andExpect(status().isOk());
        mvc.perform(post("/api/clientes").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"A\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/clientes/c1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"A\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "atendente01", roles = "ATENDENTE")
    void atendenteCadastraEAtualizaClientesMasNaoExclui() throws Exception {
        when(clienteService.salvar(any())).thenReturn(new com.umc.pediupatrao.entity.Cliente());
        mvc.perform(post("/api/clientes").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"A\",\"telefone\":\"1\"}"))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/clientes/c1").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"nome\":\"A\",\"telefone\":\"1\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/clientes/c1").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "gerente01", roles = "GERENTE")
    void gerenteExcluiCliente() throws Exception {
        mvc.perform(delete("/api/clientes/c1").with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "antigo", roles = "USER")
    void roleAntigaUserNaoTemAcessoAEndpointNenhum() throws Exception {
        mvc.perform(get("/api/pedidos")).andExpect(status().isForbidden());
        mvc.perform(get("/api/clientes")).andExpect(status().isForbidden());
        mvc.perform(get("/api/auditoria")).andExpect(status().isForbidden());
        mvc.perform(get("/api/usuarios")).andExpect(status().isForbidden());
    }
}

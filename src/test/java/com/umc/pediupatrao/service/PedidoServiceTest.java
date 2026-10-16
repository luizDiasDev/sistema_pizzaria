package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PedidoServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-14T15:30:00Z");

    @Mock PedidoRepository pedidoRepository;
    @Mock ClienteRepository clienteRepository;
    @Mock ProdutoRepository produtoRepository;
    @Mock AuditoriaService auditoriaService;
    @Captor ArgumentCaptor<Map<String, String>> antesCaptor;
    @Captor ArgumentCaptor<Map<String, String>> depoisCaptor;

    PedidoService service;

    @BeforeEach
    void setUp() {
        service = new PedidoService(pedidoRepository, clienteRepository, produtoRepository, auditoriaService,
                Clock.fixed(AGORA, ZoneOffset.UTC));
        TestSeguranca.autenticarComo("gerente01", "GERENTE");

        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId("ped1");
            }
            return p;
        });

        Cliente cliente = new Cliente();
        cliente.setId("cli1");
        cliente.setNome("Maria");
        when(clienteRepository.findById("cli1")).thenReturn(Optional.of(cliente));

        when(produtoRepository.findById("prod1")).thenReturn(Optional.of(produto("prod1", "Calabresa", 45.90, true)));
        when(produtoRepository.findById("prod2")).thenReturn(Optional.of(produto("prod2", "Refrigerante", 12.00, true)));
        when(produtoRepository.findById("inativo")).thenReturn(Optional.of(produto("inativo", "Antiga", 10.0, false)));
    }

    @AfterEach
    void tearDown() {
        TestSeguranca.limpar();
    }

    private static Produto produto(String id, String nome, double preco, boolean ativo) {
        Produto p = new Produto();
        p.setId(id);
        p.setNome(nome);
        p.setPreco(preco);
        p.setAtivo(ativo);
        return p;
    }

    private PedidoRequest request(TipoEntrega tipo, PedidoRequest.ItemRequest... itens) {
        return new PedidoRequest("cli1", tipo, List.of(itens));
    }

    private Pedido pedidoNoStatus(StatusPedido status, TipoEntrega tipo) {
        Pedido p = service.criarPedido(request(tipo, new PedidoRequest.ItemRequest("prod1", 2)));
        p.setStatus(status.name());
        when(pedidoRepository.findById("ped1")).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void criaPedidoCalculandoValoresNoServidorEComResponsavelEEntrada() {
        Pedido p = service.criarPedido(request(TipoEntrega.ENTREGA,
                new PedidoRequest.ItemRequest("prod1", 2),
                new PedidoRequest.ItemRequest("prod2", 1)));

        assertThat(p.statusAtual()).isEqualTo(StatusPedido.RECEBIDO);
        assertThat(p.getSubtotal()).isEqualByComparingTo("103.80");
        assertThat(p.getTotal()).isEqualByComparingTo("103.80");
        assertThat(p.getDescontoPercentual()).isEqualByComparingTo("0");
        assertThat(p.getCriadoPor()).isEqualTo("gerente01");
        assertThat(p.getDataEntrada()).isEqualTo(AGORA);
        assertThat(p.getClienteNome()).isEqualTo("Maria");
        assertThat(p.getItens()).hasSize(2);
        assertThat(p.getItens().get(0).getPrecoUnitario()).isEqualByComparingTo("45.90");

        verify(auditoriaService).registrar(eq(TipoOperacao.CRIACAO_PEDIDO), eq("PEDIDO"), eq("ped1"),
                any(), depoisCaptor.capture(), isNull());
        assertThat(depoisCaptor.getValue()).containsEntry("total", "103.80").containsEntry("status", "RECEBIDO");
    }

    @Test
    void rejeitaPedidoSemItens() {
        assertThatThrownBy(() -> service.criarPedido(new PedidoRequest("cli1", TipoEntrega.ENTREGA, List.of())))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.criarPedido(new PedidoRequest("cli1", TipoEntrega.ENTREGA, null)))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void rejeitaQuantidadeInvalidaProdutoInativoOuInexistenteEClienteInexistente() {
        assertThatThrownBy(() -> service.criarPedido(request(TipoEntrega.ENTREGA, new PedidoRequest.ItemRequest("prod1", 0))))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.criarPedido(request(TipoEntrega.ENTREGA, new PedidoRequest.ItemRequest("prod1", -3))))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.criarPedido(request(TipoEntrega.ENTREGA, new PedidoRequest.ItemRequest("prod1", 1000))))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.criarPedido(request(TipoEntrega.ENTREGA, new PedidoRequest.ItemRequest("inativo", 1))))
                .hasMessageContaining("inativo");
        assertThatThrownBy(() -> service.criarPedido(request(TipoEntrega.ENTREGA, new PedidoRequest.ItemRequest("nao-existe", 1))))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.criarPedido(new PedidoRequest("fantasma", TipoEntrega.ENTREGA,
                List.of(new PedidoRequest.ItemRequest("prod1", 1)))))
                .hasMessageContaining("Cliente");
        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void alteracaoDeItensRecalculaTotalEAudita() {
        pedidoNoStatus(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA);

        Pedido alterado = service.alterarPedido("ped1", request(TipoEntrega.ENTREGA, new PedidoRequest.ItemRequest("prod2", 3)));

        assertThat(alterado.getTotal()).isEqualByComparingTo("36.00");
        verify(auditoriaService).registrar(eq(TipoOperacao.ALTERACAO_PEDIDO), eq("PEDIDO"), eq("ped1"), any(), any(), isNull());
    }

    @Test
    void naoAlteraItensDepoisQueOPedidoSaiuDoRecebido() {
        pedidoNoStatus(StatusPedido.EM_PREPARACAO, TipoEntrega.ENTREGA);
        assertThatThrownBy(() -> service.alterarPedido("ped1",
                request(TipoEntrega.ENTREGA, new PedidoRequest.ItemRequest("prod2", 1))))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void percorreOFluxoCompletoDeEntregaRegistrandoSaida() {
        pedidoNoStatus(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA);

        assertThat(service.atualizarStatus("ped1", "EM_PREPARACAO").statusAtual()).isEqualTo(StatusPedido.EM_PREPARACAO);
        assertThat(service.atualizarStatus("ped1", "PRONTO").statusAtual()).isEqualTo(StatusPedido.PRONTO);

        Pedido saiu = service.atualizarStatus("ped1", "SAIU_PARA_ENTREGA");
        assertThat(saiu.statusAtual()).isEqualTo(StatusPedido.SAIU_PARA_ENTREGA);
        assertThat(saiu.getDataSaida()).isEqualTo(AGORA);
        assertThat(saiu.getSaidaPor()).isEqualTo("gerente01");

        Pedido fim = service.atualizarStatus("ped1", "FINALIZADO");
        assertThat(fim.statusAtual()).isEqualTo(StatusPedido.FINALIZADO);
        assertThat(fim.getDataFinalizacao()).isEqualTo(AGORA);
    }

    @Test
    void retiradaUsaStatusRetirado() {
        pedidoNoStatus(StatusPedido.PRONTO, TipoEntrega.RETIRADA);

        assertThatThrownBy(() -> service.atualizarStatus("ped1", "SAIU_PARA_ENTREGA"))
                .isInstanceOf(RegraNegocioException.class);
        Pedido retirado = service.atualizarStatus("ped1", "RETIRADO");
        assertThat(retirado.getDataSaida()).isEqualTo(AGORA);
        assertThat(retirado.getSaidaPor()).isEqualTo("gerente01");
    }

    @Test
    void bloqueiaTransicoesInvalidas() {
        pedidoNoStatus(StatusPedido.EM_PREPARACAO, TipoEntrega.ENTREGA);

        assertThatThrownBy(() -> service.atualizarStatus("ped1", "FINALIZADO"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Transição inválida");
        assertThatThrownBy(() -> service.atualizarStatus("ped1", "RECEBIDO"))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.atualizarStatus("ped1", "VOANDO"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("Status inválido");
    }

    @Test
    void statusCanceladoNaoPassaPeloEndpointDeStatus() {
        pedidoNoStatus(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA);
        assertThatThrownBy(() -> service.atualizarStatus("ped1", "CANCELADO"))
                .hasMessageContaining("cancelamento");
    }

    @Test
    void mudancaDeStatusEhAuditada() {
        pedidoNoStatus(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA);
        service.atualizarStatus("ped1", "EM_PREPARACAO");

        verify(auditoriaService).registrar(eq(TipoOperacao.MUDANCA_STATUS), eq("PEDIDO"), eq("ped1"),
                antesCaptor.capture(), depoisCaptor.capture(), isNull());
        assertThat(antesCaptor.getValue()).containsEntry("status", "RECEBIDO");
        assertThat(depoisCaptor.getValue()).containsEntry("status", "EM_PREPARACAO");
    }

    @Test
    void aplicaDescontoDeAte20PorCentoERecalculaTotal() {
        pedidoNoStatus(StatusPedido.EM_PREPARACAO, TipoEntrega.ENTREGA);

        Pedido p = service.aplicarDesconto("ped1", new BigDecimal("20"));

        assertThat(p.getDescontoPercentual()).isEqualByComparingTo("20");
        assertThat(p.getValorDesconto()).isEqualByComparingTo("18.36");
        assertThat(p.getTotal()).isEqualByComparingTo("73.44");
        verify(auditoriaService).registrar(eq(TipoOperacao.DESCONTO_PEDIDO), eq("PEDIDO"), eq("ped1"), any(), any(), isNull());
    }

    @Test
    void descontoNaoAcumula() {
        pedidoNoStatus(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA);
        service.aplicarDesconto("ped1", new BigDecimal("15"));
        Pedido p = service.aplicarDesconto("ped1", new BigDecimal("10"));
        assertThat(p.getTotal()).isEqualByComparingTo("82.62");
    }

    @Test
    void rejeitaDescontoAcimaDeVintePorCentoZeroNegativoOuNulo() {
        pedidoNoStatus(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA);

        assertThatThrownBy(() -> service.aplicarDesconto("ped1", new BigDecimal("20.01")))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("20");
        assertThatThrownBy(() -> service.aplicarDesconto("ped1", new BigDecimal("50")))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.aplicarDesconto("ped1", BigDecimal.ZERO))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.aplicarDesconto("ped1", new BigDecimal("-5")))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.aplicarDesconto("ped1", null))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void naoAplicaDescontoDepoisDaSaidaNemEmPedidoEncerrado() {
        for (StatusPedido st : new StatusPedido[] {StatusPedido.SAIU_PARA_ENTREGA, StatusPedido.RETIRADO,
                StatusPedido.FINALIZADO, StatusPedido.CANCELADO}) {
            pedidoNoStatus(st, TipoEntrega.ENTREGA);
            assertThatThrownBy(() -> service.aplicarDesconto("ped1", new BigDecimal("10")))
                    .as("desconto em %s", st)
                    .isInstanceOf(RegraNegocioException.class);
        }
    }

    @Test
    void cancelaComJustificativaPreservandoOsDadosEAuditando() {
        pedidoNoStatus(StatusPedido.PRONTO, TipoEntrega.ENTREGA);

        Pedido p = service.cancelar("ped1", "Cliente desistiu do pedido");

        assertThat(p.statusAtual()).isEqualTo(StatusPedido.CANCELADO);
        assertThat(p.getCanceladoPor()).isEqualTo("gerente01");
        assertThat(p.getDataCancelamento()).isEqualTo(AGORA);
        assertThat(p.getJustificativaCancelamento()).isEqualTo("Cliente desistiu do pedido");
        assertThat(p.getItens()).hasSize(1);
        assertThat(p.getTotal()).isEqualByComparingTo("91.80");
        verify(auditoriaService).registrar(eq(TipoOperacao.CANCELAMENTO_PEDIDO), eq("PEDIDO"), eq("ped1"),
                any(), any(), eq("Cliente desistiu do pedido"));
    }

    @Test
    void cancelamentoExigeJustificativa() {
        pedidoNoStatus(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA);
        assertThatThrownBy(() -> service.cancelar("ped1", null)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.cancelar("ped1", "   ")).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.cancelar("ped1", "ok")).isInstanceOf(RegraNegocioException.class);
        assertThat(service.buscarPorId("ped1").statusAtual()).isEqualTo(StatusPedido.RECEBIDO);
    }

    @Test
    void naoCancelaDepoisDaSaidaParaEntregaOuRetirada() {
        for (StatusPedido st : new StatusPedido[] {StatusPedido.SAIU_PARA_ENTREGA, StatusPedido.RETIRADO, StatusPedido.FINALIZADO}) {
            pedidoNoStatus(st, TipoEntrega.ENTREGA);
            assertThatThrownBy(() -> service.cancelar("ped1", "Justificativa válida"))
                    .as("cancelar em %s", st)
                    .isInstanceOf(RegraNegocioException.class);
        }
    }

    @Test
    void pedidoCanceladoNaoSofreNenhumaAlteracaoPosterior() {
        pedidoNoStatus(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA);
        service.cancelar("ped1", "Pedido duplicado");

        assertThatThrownBy(() -> service.atualizarStatus("ped1", "EM_PREPARACAO")).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.aplicarDesconto("ped1", new BigDecimal("5"))).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.cancelar("ped1", "Outra justificativa")).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.alterarPedido("ped1",
                request(TipoEntrega.ENTREGA, new PedidoRequest.ItemRequest("prod2", 1))))
                .isInstanceOf(RegraNegocioException.class);
    }
}

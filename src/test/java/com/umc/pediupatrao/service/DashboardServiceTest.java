package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.DashboardFiltro;
import com.umc.pediupatrao.dto.DashboardResumo;
import com.umc.pediupatrao.dto.ProdutoVendido;
import com.umc.pediupatrao.dto.StatusQuantidade;
import com.umc.pediupatrao.dto.VendaDiaria;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 11, 10);

    private PedidoRepository repo;
    private DashboardService service;

    @BeforeEach
    void setUp() {
        repo = mock(PedidoRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneOffset.UTC);
        service = new DashboardService(repo, clock);
        when(repo.buscarPorPeriodo(any(), any())).thenReturn(List.of(
                pedido("2026-11-08T15:00:00Z", TipoEntrega.ENTREGA, StatusPedido.FINALIZADO,
                        item("Pizza Calabresa", "45.90", 2)),
                pedido("2026-11-09T15:00:00Z", TipoEntrega.RETIRADA, StatusPedido.EM_PREPARACAO,
                        item("Refrigerante 2L", "12.00", 1), item("Pizza Calabresa", "45.90", 1)),
                pedido("2026-11-10T15:00:00Z", TipoEntrega.ENTREGA, StatusPedido.CANCELADO,
                        item("Pizza Mussarela", "42.00", 5))));
    }

    @Test
    void periodoPadraoSaoOsUltimos30Dias() {
        DashboardFiltro filtro = service.filtro(null, null, null);
        assertThat(filtro.fim()).isEqualTo(HOJE);
        assertThat(filtro.inicio()).isEqualTo(HOJE.minusDays(29));
    }

    @Test
    void consultaOBancoComOPeriodoNoFusoDoBrasil() {
        service.resumo(service.filtro(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 10), null));
        verify(repo).buscarPorPeriodo(Instant.parse("2026-11-08T03:00:00Z"), Instant.parse("2026-11-11T03:00:00Z"));
    }

    @Test
    void rejeitaDataInicialMaiorQueAFinal() {
        assertThatThrownBy(() -> service.filtro(HOJE, HOJE.minusDays(1), null))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("data inicial");
    }

    @Test
    void rejeitaPeriodoMaiorQueUmAno() {
        assertThatThrownBy(() -> service.filtro(HOJE.minusDays(400), HOJE, null))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void resumoIgnoraCanceladosNoFaturamentoENoTicketMedio() {
        DashboardResumo r = service.resumo(service.filtro(HOJE.minusDays(2), HOJE, null));
        assertThat(r.totalPedidos()).isEqualTo(3);
        assertThat(r.pedidosCancelados()).isEqualTo(1);
        assertThat(r.faturamento()).isEqualByComparingTo("149.70");
        assertThat(r.ticketMedio()).isEqualByComparingTo("74.85");
    }

    @Test
    void filtroPorTipoDeEntrega() {
        DashboardResumo r = service.resumo(service.filtro(HOJE.minusDays(2), HOJE, TipoEntrega.RETIRADA));
        assertThat(r.totalPedidos()).isEqualTo(1);
        assertThat(r.faturamento()).isEqualByComparingTo("57.90");
    }

    @Test
    void vendasPorDiaIncluiDiasSemVenda() {
        List<VendaDiaria> dias = service.vendasPorDia(service.filtro(HOJE.minusDays(3), HOJE, null));
        assertThat(dias).hasSize(4);
        assertThat(dias.get(0).faturamento()).isEqualByComparingTo("0");
        assertThat(dias.get(1).faturamento()).isEqualByComparingTo("91.80");
        assertThat(dias.get(3).pedidos()).isZero();
    }

    @Test
    void contaPedidosEmTodasAsSituacoes() {
        List<StatusQuantidade> status = service.pedidosPorStatus(service.filtro(HOJE.minusDays(2), HOJE, null));
        assertThat(status).hasSize(StatusPedido.values().length);
        assertThat(status).filteredOn(s -> s.status().equals("CANCELADO")).first()
                .extracting(StatusQuantidade::quantidade).isEqualTo(1L);
        assertThat(status).filteredOn(s -> s.status().equals("PRONTO")).first()
                .extracting(StatusQuantidade::quantidade).isEqualTo(0L);
    }

    @Test
    void rankingDeProdutosPorQuantidadeSemCancelados() {
        List<ProdutoVendido> ranking = service.produtosMaisVendidos(service.filtro(HOJE.minusDays(2), HOJE, null), 5);
        assertThat(ranking).extracting(ProdutoVendido::produto).containsExactly("Pizza Calabresa", "Refrigerante 2L");
        assertThat(ranking.get(0).quantidade()).isEqualTo(3);
        assertThat(ranking.get(0).faturamento()).isEqualByComparingTo("137.70");
    }

    @Test
    void rejeitaLimiteForaDaFaixa() {
        DashboardFiltro filtro = service.filtro(null, null, null);
        assertThatThrownBy(() -> service.produtosMaisVendidos(filtro, 0)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.produtosMaisVendidos(filtro, 21)).isInstanceOf(RegraNegocioException.class);
    }

    private static ItemPedido item(String nome, String preco, int quantidade) {
        return new ItemPedido("id-" + nome, nome, new BigDecimal(preco), quantidade);
    }

    private static Pedido pedido(String entrada, TipoEntrega tipo, StatusPedido status, ItemPedido... itens) {
        Pedido p = new Pedido();
        p.setDataEntrada(Instant.parse(entrada));
        p.setTipoEntrega(tipo);
        p.setStatus(status.name());
        p.setItens(List.of(itens));
        p.recalcularTotais();
        return p;
    }
}

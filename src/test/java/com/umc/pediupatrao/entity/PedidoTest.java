package com.umc.pediupatrao.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PedidoTest {

    private Pedido pedidoCom(ItemPedido... itens) {
        Pedido p = new Pedido();
        p.setItens(List.of(itens));
        return p;
    }

    @Test
    void calculaSubtotalETotalSemDesconto() {
        Pedido p = pedidoCom(
                new ItemPedido("1", "Calabresa", new BigDecimal("45.90"), 2),
                new ItemPedido("2", "Refrigerante", new BigDecimal("12.00"), 1));
        p.recalcularTotais();

        assertThat(p.getSubtotal()).isEqualByComparingTo("103.80");
        assertThat(p.getValorDesconto()).isEqualByComparingTo("0.00");
        assertThat(p.getTotal()).isEqualByComparingTo("103.80");
    }

    @Test
    void aplicaDescontoPercentualComArredondamento() {
        Pedido p = pedidoCom(new ItemPedido("1", "Calabresa", new BigDecimal("45.90"), 2));
        p.setDescontoPercentual(new BigDecimal("20.00"));
        p.recalcularTotais();

        assertThat(p.getSubtotal()).isEqualByComparingTo("91.80");
        assertThat(p.getValorDesconto()).isEqualByComparingTo("18.36");
        assertThat(p.getTotal()).isEqualByComparingTo("73.44");
    }

    @Test
    void documentoAntigoSemStatusEhTratadoComoRecebido() {
        Pedido p = new Pedido();
        assertThat(p.statusAtual()).isEqualTo(StatusPedido.RECEBIDO);
        assertThat(p.tipoEfetivo()).isEqualTo(TipoEntrega.ENTREGA);
    }

    @Test
    void proximoStatusEsperadoDependeDaFormaDeEntrega() {
        Pedido p = new Pedido();
        p.setStatus("PRONTO");
        p.setTipoEntrega(TipoEntrega.RETIRADA);
        assertThat(p.proximoStatusEsperado()).isEqualTo(StatusPedido.RETIRADO);
        p.setTipoEntrega(TipoEntrega.ENTREGA);
        assertThat(p.proximoStatusEsperado()).isEqualTo(StatusPedido.SAIU_PARA_ENTREGA);
        p.setStatus("CANCELADO");
        assertThat(p.proximoStatusEsperado()).isNull();
    }
}

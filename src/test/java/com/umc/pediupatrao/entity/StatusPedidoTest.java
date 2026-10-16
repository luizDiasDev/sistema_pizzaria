package com.umc.pediupatrao.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class StatusPedidoTest {

    @Test
    void fluxoDeEntregaSegueAOrdemObrigatoria() {
        assertThat(StatusPedido.RECEBIDO.proximos(TipoEntrega.ENTREGA)).containsExactly(StatusPedido.EM_PREPARACAO);
        assertThat(StatusPedido.EM_PREPARACAO.proximos(TipoEntrega.ENTREGA)).containsExactly(StatusPedido.PRONTO);
        assertThat(StatusPedido.PRONTO.proximos(TipoEntrega.ENTREGA)).containsExactly(StatusPedido.SAIU_PARA_ENTREGA);
        assertThat(StatusPedido.SAIU_PARA_ENTREGA.proximos(TipoEntrega.ENTREGA)).containsExactly(StatusPedido.FINALIZADO);
    }

    @Test
    void fluxoDeRetiradaUsaRetirado() {
        assertThat(StatusPedido.PRONTO.proximos(TipoEntrega.RETIRADA)).containsExactly(StatusPedido.RETIRADO);
        assertThat(StatusPedido.RETIRADO.proximos(TipoEntrega.RETIRADA)).containsExactly(StatusPedido.FINALIZADO);
        assertThat(StatusPedido.PRONTO.podeIrPara(StatusPedido.SAIU_PARA_ENTREGA, TipoEntrega.RETIRADA)).isFalse();
    }

    @Test
    void naoPermitePularEtapasNemVoltar() {
        assertThat(StatusPedido.EM_PREPARACAO.podeIrPara(StatusPedido.FINALIZADO, TipoEntrega.ENTREGA)).isFalse();
        assertThat(StatusPedido.RECEBIDO.podeIrPara(StatusPedido.PRONTO, TipoEntrega.ENTREGA)).isFalse();
        assertThat(StatusPedido.PRONTO.podeIrPara(StatusPedido.RECEBIDO, TipoEntrega.ENTREGA)).isFalse();
    }

    @Test
    void estadosEncerradosNaoTemProximos() {
        for (TipoEntrega tipo : TipoEntrega.values()) {
            assertThat(StatusPedido.FINALIZADO.proximos(tipo)).isEmpty();
            assertThat(StatusPedido.CANCELADO.proximos(tipo)).isEmpty();
        }
    }

    @Test
    void cancelamentoNuncaEhTransicaoNormal() {
        for (StatusPedido s : StatusPedido.values()) {
            for (TipoEntrega tipo : TipoEntrega.values()) {
                assertThat(s.podeIrPara(StatusPedido.CANCELADO, tipo)).isFalse();
            }
        }
    }

    @Test
    void antesDaSaidaSaoApenasOsTresPrimeirosEstados() {
        assertThat(EnumSet.allOf(StatusPedido.class).stream().filter(StatusPedido::antesDaSaida))
                .containsExactlyInAnyOrder(StatusPedido.RECEBIDO, StatusPedido.EM_PREPARACAO, StatusPedido.PRONTO);
    }

    @Test
    void parseConverteONomeDoStatus() {
        assertThat(StatusPedido.parse("EM_PREPARACAO")).contains(StatusPedido.EM_PREPARACAO);
        assertThat(StatusPedido.parse("saiu_para_entrega")).contains(StatusPedido.SAIU_PARA_ENTREGA);
        assertThat(StatusPedido.parse("qualquer coisa")).isEmpty();
        assertThat(StatusPedido.parse(null)).isEmpty();
    }
}

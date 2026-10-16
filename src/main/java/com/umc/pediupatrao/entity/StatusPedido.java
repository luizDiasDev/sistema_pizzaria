package com.umc.pediupatrao.entity;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public enum StatusPedido {
    RECEBIDO("Recebido"),
    EM_PREPARACAO("Em preparação"),
    PRONTO("Pronto"),
    SAIU_PARA_ENTREGA("Saiu para entrega"),
    RETIRADO("Retirado"),
    FINALIZADO("Finalizado"),
    CANCELADO("Cancelado");

    private final String rotulo;

    StatusPedido(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }

    public boolean antesDaSaida() {
        return this == RECEBIDO || this == EM_PREPARACAO || this == PRONTO;
    }

    public boolean ehSaida() {
        return this == SAIU_PARA_ENTREGA || this == RETIRADO;
    }

    public boolean ehEncerrado() {
        return this == FINALIZADO || this == CANCELADO;
    }

    public Set<StatusPedido> proximos(TipoEntrega tipo) {
        switch (this) {
            case RECEBIDO:
                return EnumSet.of(EM_PREPARACAO);
            case EM_PREPARACAO:
                return EnumSet.of(PRONTO);
            case PRONTO:
                return tipo == TipoEntrega.RETIRADA
                        ? EnumSet.of(RETIRADO)
                        : EnumSet.of(SAIU_PARA_ENTREGA);
            case SAIU_PARA_ENTREGA:
            case RETIRADO:
                return EnumSet.of(FINALIZADO);
            default:
                return EnumSet.noneOf(StatusPedido.class);
        }
    }

    public boolean podeIrPara(StatusPedido destino, TipoEntrega tipo) {
        return proximos(tipo).contains(destino);
    }

    public static Optional<StatusPedido> parse(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(texto.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

package com.umc.pediupatrao.entity;

public enum TipoOperacao {
    CRIACAO_PEDIDO("Criação de pedido"),
    ALTERACAO_PEDIDO("Alteração de pedido"),
    MUDANCA_STATUS("Mudança de status"),
    DESCONTO_PEDIDO("Desconto em pedido"),
    CANCELAMENTO_PEDIDO("Cancelamento de pedido"),
    CRIACAO_CLIENTE("Criação de cliente"),
    ALTERACAO_CLIENTE("Alteração de cliente"),
    EXCLUSAO_CLIENTE("Exclusão de cliente");

    private final String descricao;

    TipoOperacao(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}

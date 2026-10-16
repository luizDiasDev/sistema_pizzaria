package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.TipoEntrega;
import java.util.List;

public record PedidoRequest(String clienteId, TipoEntrega tipoEntrega, List<ItemRequest> itens) {

    public record ItemRequest(String produtoId, Integer quantidade) {
    }
}

package com.umc.pediupatrao.dto;

import java.math.BigDecimal;

public record ProdutoVendido(String produto, long quantidade, BigDecimal faturamento) {
}

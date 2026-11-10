package com.umc.pediupatrao.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VendaDiaria(LocalDate data, long pedidos, BigDecimal faturamento) {
}

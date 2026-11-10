package com.umc.pediupatrao.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DashboardResumo(
        LocalDate inicio,
        LocalDate fim,
        BigDecimal faturamento,
        long totalPedidos,
        long pedidosCancelados,
        BigDecimal ticketMedio,
        BigDecimal totalDescontos) {
}

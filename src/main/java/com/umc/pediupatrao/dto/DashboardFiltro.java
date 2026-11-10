package com.umc.pediupatrao.dto;

import com.umc.pediupatrao.entity.TipoEntrega;
import java.time.LocalDate;

public record DashboardFiltro(LocalDate inicio, LocalDate fim, TipoEntrega tipoEntrega) {
}

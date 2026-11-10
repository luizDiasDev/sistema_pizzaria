package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.DashboardResumo;
import com.umc.pediupatrao.dto.ProdutoVendido;
import com.umc.pediupatrao.dto.StatusQuantidade;
import com.umc.pediupatrao.dto.VendaDiaria;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.service.DashboardService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/resumo")
    public DashboardResumo resumo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) TipoEntrega tipoEntrega) {
        return dashboardService.resumo(dashboardService.filtro(inicio, fim, tipoEntrega));
    }

    @GetMapping("/vendas-por-dia")
    public List<VendaDiaria> vendasPorDia(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) TipoEntrega tipoEntrega) {
        return dashboardService.vendasPorDia(dashboardService.filtro(inicio, fim, tipoEntrega));
    }

    @GetMapping("/pedidos-por-status")
    public List<StatusQuantidade> pedidosPorStatus(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) TipoEntrega tipoEntrega) {
        return dashboardService.pedidosPorStatus(dashboardService.filtro(inicio, fim, tipoEntrega));
    }

    @GetMapping("/produtos-mais-vendidos")
    public List<ProdutoVendido> produtosMaisVendidos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
            @RequestParam(required = false) TipoEntrega tipoEntrega,
            @RequestParam(defaultValue = "5") int limite) {
        return dashboardService.produtosMaisVendidos(dashboardService.filtro(inicio, fim, tipoEntrega), limite);
    }
}

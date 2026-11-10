package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.DashboardFiltro;
import com.umc.pediupatrao.dto.DashboardResumo;
import com.umc.pediupatrao.dto.ProdutoVendido;
import com.umc.pediupatrao.dto.StatusQuantidade;
import com.umc.pediupatrao.dto.VendaDiaria;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
public class DashboardService {

    public static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    static final int DIAS_PADRAO = 30;
    static final int DIAS_MAXIMO = 366;
    static final int LIMITE_MAXIMO = 20;

    private final PedidoRepository pedidoRepository;
    private final Clock clock;

    public DashboardService(PedidoRepository pedidoRepository, Clock clock) {
        this.pedidoRepository = pedidoRepository;
        this.clock = clock;
    }

    public DashboardFiltro filtro(LocalDate inicio, LocalDate fim, TipoEntrega tipoEntrega) {
        LocalDate fimEfetivo = fim != null ? fim : LocalDate.now(clock.withZone(ZONA));
        LocalDate inicioEfetivo = inicio != null ? inicio : fimEfetivo.minusDays(DIAS_PADRAO - 1L);
        if (inicioEfetivo.isAfter(fimEfetivo)) {
            throw new RegraNegocioException("A data inicial não pode ser maior que a final.", HttpStatus.BAD_REQUEST);
        }
        if (ChronoUnit.DAYS.between(inicioEfetivo, fimEfetivo) >= DIAS_MAXIMO) {
            throw new RegraNegocioException("O período máximo é de " + DIAS_MAXIMO + " dias.", HttpStatus.BAD_REQUEST);
        }
        return new DashboardFiltro(inicioEfetivo, fimEfetivo, tipoEntrega);
    }

    public DashboardResumo resumo(DashboardFiltro filtro) {
        List<Pedido> pedidos = buscar(filtro);
        List<Pedido> validos = naoCancelados(pedidos);
        BigDecimal faturamento = somar(validos.stream().map(Pedido::getTotal).toList());
        BigDecimal descontos = somar(validos.stream().map(Pedido::getValorDesconto).toList());
        BigDecimal ticketMedio = validos.isEmpty()
                ? BigDecimal.ZERO.setScale(2)
                : faturamento.divide(BigDecimal.valueOf(validos.size()), 2, RoundingMode.HALF_UP);
        return new DashboardResumo(filtro.inicio(), filtro.fim(), faturamento, pedidos.size(),
                pedidos.size() - validos.size(), ticketMedio, descontos);
    }

    public List<VendaDiaria> vendasPorDia(DashboardFiltro filtro) {
        Map<LocalDate, List<Pedido>> porDia = new LinkedHashMap<>();
        for (LocalDate d = filtro.inicio(); !d.isAfter(filtro.fim()); d = d.plusDays(1)) {
            porDia.put(d, new ArrayList<>());
        }
        for (Pedido p : naoCancelados(buscar(filtro))) {
            List<Pedido> doDia = porDia.get(p.getDataEntrada().atZone(ZONA).toLocalDate());
            if (doDia != null) {
                doDia.add(p);
            }
        }
        return porDia.entrySet().stream()
                .map(e -> new VendaDiaria(e.getKey(), e.getValue().size(),
                        somar(e.getValue().stream().map(Pedido::getTotal).toList())))
                .toList();
    }

    public List<StatusQuantidade> pedidosPorStatus(DashboardFiltro filtro) {
        Map<StatusPedido, Long> contagem = new LinkedHashMap<>();
        for (StatusPedido s : StatusPedido.values()) {
            contagem.put(s, 0L);
        }
        for (Pedido p : buscar(filtro)) {
            contagem.merge(p.statusAtual(), 1L, Long::sum);
        }
        return contagem.entrySet().stream()
                .map(e -> new StatusQuantidade(e.getKey().name(), e.getKey().getRotulo(), e.getValue()))
                .toList();
    }

    public List<ProdutoVendido> produtosMaisVendidos(DashboardFiltro filtro, int limite) {
        if (limite < 1 || limite > LIMITE_MAXIMO) {
            throw new RegraNegocioException("O limite deve estar entre 1 e " + LIMITE_MAXIMO + ".", HttpStatus.BAD_REQUEST);
        }
        Map<String, long[]> quantidades = new LinkedHashMap<>();
        Map<String, BigDecimal> valores = new LinkedHashMap<>();
        for (Pedido p : naoCancelados(buscar(filtro))) {
            for (ItemPedido item : p.getItens()) {
                String nome = item.getNomeProduto() == null ? "(sem nome)" : item.getNomeProduto();
                quantidades.computeIfAbsent(nome, k -> new long[1])[0] += item.getQuantidade();
                valores.merge(nome, valorOuZero(item.getSubtotal()), BigDecimal::add);
            }
        }
        return quantidades.entrySet().stream()
                .map(e -> new ProdutoVendido(e.getKey(), e.getValue()[0],
                        valores.get(e.getKey()).setScale(2, RoundingMode.HALF_UP)))
                .sorted(Comparator.comparingLong(ProdutoVendido::quantidade).reversed()
                        .thenComparing(ProdutoVendido::produto))
                .limit(limite)
                .toList();
    }

    private List<Pedido> buscar(DashboardFiltro filtro) {
        return pedidoRepository.buscarPorPeriodo(
                        filtro.inicio().atStartOfDay(ZONA).toInstant(),
                        filtro.fim().plusDays(1).atStartOfDay(ZONA).toInstant())
                .stream()
                .filter(p -> p.getDataEntrada() != null)
                .filter(p -> filtro.tipoEntrega() == null || p.tipoEfetivo() == filtro.tipoEntrega())
                .toList();
    }

    private static List<Pedido> naoCancelados(List<Pedido> pedidos) {
        return pedidos.stream().filter(p -> p.statusAtual() != StatusPedido.CANCELADO).toList();
    }

    private static BigDecimal somar(List<BigDecimal> valores) {
        return valores.stream().map(DashboardService::valorOuZero)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal valorOuZero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }
}

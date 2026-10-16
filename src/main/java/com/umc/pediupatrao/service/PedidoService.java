package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.security.ContextoUsuario;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class PedidoService {

    static final String ENTIDADE = "PEDIDO";
    public static final BigDecimal DESCONTO_MAXIMO_PERCENTUAL = new BigDecimal("20");
    static final int MAXIMO_LINHAS = 50;
    static final int QUANTIDADE_MAXIMA_POR_ITEM = 99;
    static final int TAMANHO_MINIMO_JUSTIFICATIVA = 5;

    private final PedidoRepository pedidoRepository;
    private final ClienteRepository clienteRepository;
    private final ProdutoRepository produtoRepository;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public PedidoService(PedidoRepository pedidoRepository, ClienteRepository clienteRepository,
            ProdutoRepository produtoRepository, AuditoriaService auditoriaService, Clock clock) {
        this.pedidoRepository = pedidoRepository;
        this.clienteRepository = clienteRepository;
        this.produtoRepository = produtoRepository;
        this.auditoriaService = auditoriaService;
        this.clock = clock;
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public List<Pedido> listarPedidos() {
        List<Pedido> pedidos = new ArrayList<>(pedidoRepository.findAll());
        pedidos.sort(Comparator.comparing(Pedido::getDataEntrada,
                Comparator.nullsLast(Comparator.<Instant>reverseOrder())));
        return pedidos;
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public Pedido buscarPorId(String id) {
        return buscar(id);
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido criarPedido(PedidoRequest request) {
        if (request == null) {
            throw new RegraNegocioException("Dados do pedido não informados.");
        }
        Cliente cliente = buscarCliente(request.clienteId());

        Pedido pedido = new Pedido();
        pedido.setClienteId(cliente.getId());
        pedido.setClienteNome(cliente.getNome());
        pedido.setTipoEntrega(request.tipoEntrega() == null ? TipoEntrega.ENTREGA : request.tipoEntrega());
        pedido.setItens(montarItens(request.itens()));
        pedido.setStatus(StatusPedido.RECEBIDO.name());
        pedido.setDescontoPercentual(BigDecimal.ZERO);
        pedido.setCriadoPor(ContextoUsuario.username());
        pedido.setDataEntrada(Instant.now(clock));
        pedido.recalcularTotais();

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(TipoOperacao.CRIACAO_PEDIDO, ENTIDADE, salvo.getId(),
                Map.of(), snapshot(salvo), null);
        return salvo;
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Pedido alterarPedido(String id, PedidoRequest request) {
        if (request == null) {
            throw new RegraNegocioException("Dados do pedido não informados.");
        }
        Pedido pedido = buscar(id);
        bloquearSeEncerrado(pedido);
        if (pedido.statusAtual() != StatusPedido.RECEBIDO) {
            throw RegraNegocioException.conflito("O pedido só pode ser alterado enquanto estiver RECEBIDO.");
        }
        Map<String, String> antes = snapshot(pedido);

        if (request.clienteId() != null && !request.clienteId().equals(pedido.getClienteId())) {
            Cliente cliente = buscarCliente(request.clienteId());
            pedido.setClienteId(cliente.getId());
            pedido.setClienteNome(cliente.getNome());
        }
        if (request.tipoEntrega() != null) {
            pedido.setTipoEntrega(request.tipoEntrega());
        }
        if (request.itens() != null) {
            pedido.setItens(montarItens(request.itens()));
        }
        pedido.recalcularTotais();

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(TipoOperacao.ALTERACAO_PEDIDO, ENTIDADE, salvo.getId(),
                antes, snapshot(salvo), null);
        return salvo;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido atualizarStatus(String id, String novoStatus) {
        StatusPedido destino = StatusPedido.parse(novoStatus)
                .orElseThrow(() -> new RegraNegocioException("Status inválido: " + novoStatus));
        if (destino == StatusPedido.CANCELADO) {
            throw new RegraNegocioException("Para cancelar use a operação de cancelamento, que exige justificativa.");
        }

        Pedido pedido = buscar(id);
        bloquearSeEncerrado(pedido);
        StatusPedido atual = pedido.statusAtual();
        if (!atual.podeIrPara(destino, pedido.tipoEfetivo())) {
            throw RegraNegocioException.conflito("Transição inválida: " + atual.name() + " → " + destino.name()
                    + ". Próximo passo permitido: " + pedido.proximoStatusEsperado() + ".");
        }

        Map<String, String> antes = snapshot(pedido);
        Instant agora = Instant.now(clock);
        pedido.setStatus(destino.name());
        if (destino.ehSaida()) {
            pedido.setDataSaida(agora);
            pedido.setSaidaPor(ContextoUsuario.username());
        }
        if (destino == StatusPedido.FINALIZADO) {
            pedido.setDataFinalizacao(agora);
        }

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(TipoOperacao.MUDANCA_STATUS, ENTIDADE, salvo.getId(),
                antes, snapshot(salvo), null);
        return salvo;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido aplicarDesconto(String id, BigDecimal percentual) {
        if (percentual == null) {
            throw new RegraNegocioException("Informe o percentual de desconto.");
        }
        if (percentual.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RegraNegocioException("O desconto deve ser maior que zero.");
        }
        if (percentual.compareTo(DESCONTO_MAXIMO_PERCENTUAL) > 0) {
            throw new RegraNegocioException("O desconto máximo permitido é de "
                    + DESCONTO_MAXIMO_PERCENTUAL.toPlainString() + "% do valor do pedido.");
        }

        Pedido pedido = buscar(id);
        bloquearSeEncerrado(pedido);
        if (!pedido.statusAtual().antesDaSaida()) {
            throw RegraNegocioException.conflito("O desconto só pode ser aplicado antes da saída para entrega ou retirada.");
        }

        Map<String, String> antes = snapshot(pedido);
        pedido.setDescontoPercentual(percentual.setScale(2, RoundingMode.HALF_UP));
        pedido.recalcularTotais();

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(TipoOperacao.DESCONTO_PEDIDO, ENTIDADE, salvo.getId(),
                antes, snapshot(salvo), null);
        return salvo;
    }

    @PreAuthorize("hasRole('GERENTE')")
    public Pedido cancelar(String id, String justificativa) {
        String motivo = textoOuNull(justificativa);
        if (motivo == null || motivo.length() < TAMANHO_MINIMO_JUSTIFICATIVA) {
            throw new RegraNegocioException("Informe a justificativa do cancelamento (mínimo de "
                    + TAMANHO_MINIMO_JUSTIFICATIVA + " caracteres).");
        }

        Pedido pedido = buscar(id);
        bloquearSeEncerrado(pedido);
        if (!pedido.statusAtual().antesDaSaida()) {
            throw RegraNegocioException.conflito("O pedido não pode ser cancelado após a saída para entrega ou retirada.");
        }

        Map<String, String> antes = snapshot(pedido);
        pedido.setStatus(StatusPedido.CANCELADO.name());
        pedido.setCanceladoPor(ContextoUsuario.username());
        pedido.setDataCancelamento(Instant.now(clock));
        pedido.setJustificativaCancelamento(motivo);

        Pedido salvo = pedidoRepository.save(pedido);
        auditoriaService.registrar(TipoOperacao.CANCELAMENTO_PEDIDO, ENTIDADE, salvo.getId(),
                antes, snapshot(salvo), motivo);
        return salvo;
    }

    private Pedido buscar(String id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado."));
    }

    private Cliente buscarCliente(String clienteId) {
        if (clienteId == null || clienteId.isBlank()) {
            throw new RegraNegocioException("Informe o cliente do pedido.");
        }
        return clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RegraNegocioException("Cliente não encontrado."));
    }

    private void bloquearSeEncerrado(Pedido pedido) {
        StatusPedido atual = pedido.statusAtual();
        if (atual == StatusPedido.CANCELADO) {
            throw RegraNegocioException.conflito("Pedido cancelado não pode sofrer alterações.");
        }
        if (atual == StatusPedido.FINALIZADO) {
            throw RegraNegocioException.conflito("Pedido finalizado não pode sofrer alterações.");
        }
    }

    private List<ItemPedido> montarItens(List<PedidoRequest.ItemRequest> solicitados) {
        if (solicitados == null || solicitados.isEmpty()) {
            throw new RegraNegocioException("O pedido deve ter pelo menos um item.");
        }
        if (solicitados.size() > MAXIMO_LINHAS) {
            throw new RegraNegocioException("O pedido pode ter no máximo " + MAXIMO_LINHAS + " itens.");
        }
        List<ItemPedido> itens = new ArrayList<>();
        for (PedidoRequest.ItemRequest s : solicitados) {
            if (s == null || s.produtoId() == null || s.produtoId().isBlank()) {
                throw new RegraNegocioException("Informe o produto de cada item.");
            }
            Integer quantidade = s.quantidade();
            if (quantidade == null || quantidade < 1 || quantidade > QUANTIDADE_MAXIMA_POR_ITEM) {
                throw new RegraNegocioException("A quantidade de cada item deve estar entre 1 e "
                        + QUANTIDADE_MAXIMA_POR_ITEM + ".");
            }
            Produto produto = produtoRepository.findById(s.produtoId())
                    .orElseThrow(() -> new RegraNegocioException("Produto não encontrado: " + s.produtoId()));
            if (Boolean.FALSE.equals(produto.getAtivo())) {
                throw new RegraNegocioException("Produto inativo: " + produto.getNome());
            }
            if (produto.getPreco() == null || produto.getPreco() <= 0) {
                throw new RegraNegocioException("Produto sem preço válido: " + produto.getNome());
            }
            itens.add(new ItemPedido(produto.getId(), produto.getNome(),
                    BigDecimal.valueOf(produto.getPreco()), quantidade));
        }
        return itens;
    }

    private static String textoOuNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    static Map<String, String> snapshot(Pedido p) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("clienteId", p.getClienteId());
        m.put("tipoEntrega", p.getTipoEntrega() == null ? null : p.getTipoEntrega().name());
        m.put("itens", p.getItens().isEmpty() ? null : p.resumoItens());
        m.put("status", p.getStatus());
        m.put("subtotal", plain(p.getSubtotal()));
        m.put("descontoPercentual", plain(p.getDescontoPercentual()));
        m.put("valorDesconto", plain(p.getValorDesconto()));
        m.put("total", plain(p.getTotal()));
        m.put("dataSaida", p.getDataSaida() == null ? null : p.getDataSaida().toString());
        m.put("saidaPor", p.getSaidaPor());
        return m;
    }

    private static String plain(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }
}

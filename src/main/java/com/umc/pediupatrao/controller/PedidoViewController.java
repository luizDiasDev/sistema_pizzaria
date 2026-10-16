package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PedidoViewController {

    private final PedidoService pedidoService;
    private final ClienteService clienteService;
    private final ProdutoService produtoService;

    public PedidoViewController(PedidoService pedidoService, ClienteService clienteService,
            ProdutoService produtoService) {
        this.pedidoService = pedidoService;
        this.clienteService = clienteService;
        this.produtoService = produtoService;
    }

    @GetMapping("/pedidos")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE','ATENDENTE')")
    public String listar(Model model) {
        model.addAttribute("pedidos", pedidoService.listarPedidos());
        model.addAttribute("content", "pedidos :: content");
        return "layout";
    }

    @GetMapping("/pedidos/novo")
    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public String novo(Model model) {
        List<Map<String, String>> clientes = clienteService.listarClientes().stream()
                .map((Cliente c) -> Map.of("id", c.getId(), "nome", c.getNome() == null ? "(sem nome)" : c.getNome()))
                .toList();
        List<Produto> produtos = produtoService.listarProdutos().stream()
                .filter(p -> !Boolean.FALSE.equals(p.getAtivo()) && p.getPreco() != null && p.getPreco() > 0)
                .toList();
        model.addAttribute("clientes", clientes);
        model.addAttribute("produtos", produtos);
        model.addAttribute("content", "pedidos-novo :: content");
        return "layout";
    }

    @PostMapping("/pedidos/{id}/avancar")
    @PreAuthorize("hasRole('GERENTE')")
    public String avancar(@PathVariable String id, @RequestParam String status, RedirectAttributes ra) {
        return executar(ra, "Status atualizado.", () -> pedidoService.atualizarStatus(id, status));
    }

    @PostMapping("/pedidos/{id}/desconto")
    @PreAuthorize("hasRole('GERENTE')")
    public String desconto(@PathVariable String id, @RequestParam BigDecimal percentual, RedirectAttributes ra) {
        return executar(ra, "Desconto aplicado.", () -> pedidoService.aplicarDesconto(id, percentual));
    }

    @PostMapping("/pedidos/{id}/cancelar")
    @PreAuthorize("hasRole('GERENTE')")
    public String cancelar(@PathVariable String id, @RequestParam String justificativa, RedirectAttributes ra) {
        return executar(ra, "Pedido cancelado.", () -> pedidoService.cancelar(id, justificativa));
    }

    private String executar(RedirectAttributes ra, String sucesso, Runnable acao) {
        try {
            acao.run();
            ra.addFlashAttribute("sucesso", sucesso);
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            ra.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos";
    }
}

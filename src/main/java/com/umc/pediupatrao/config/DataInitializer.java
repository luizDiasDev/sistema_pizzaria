package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository repo, ProdutoRepository produtoRepo,
            PedidoRepository pedidoRepo, PasswordEncoder encoder,
            @Value("${app.seed.enabled:true}") boolean habilitado,
            @Value("${app.seed.pedidos-demo:true}") boolean pedidosDemo,
            @Value("${app.seed.admin-senha:teste123}") String senhaAdmin,
            @Value("${app.seed.gerente-senha:gerente123}") String senhaGerente,
            @Value("${app.seed.atendente-senha:atendente123}") String senhaAtendente) {
        return args -> {
            if (!habilitado) {
                return;
            }

            repo.findAll().forEach(u -> {
                if (Perfil.de(u.getRole()).isEmpty()) {
                    Perfil novo = "admin".equals(u.getUsername()) ? Perfil.ADMIN : Perfil.ATENDENTE;
                    log.warn("Migrando usuário '{}' do perfil '{}' para {}", u.getUsername(), u.getRole(), novo);
                    u.setRole(novo.name());
                    repo.save(u);
                }
            });

            criarSeAusente(repo, encoder, "admin", senhaAdmin, Perfil.ADMIN);
            criarSeAusente(repo, encoder, "gerente", senhaGerente, Perfil.GERENTE);
            criarSeAusente(repo, encoder, "atendente", senhaAtendente, Perfil.ATENDENTE);

            if (produtoRepo.count() == 0) {
                produto(produtoRepo, "Pizza Calabresa", "Pizza", 45.90);
                produto(produtoRepo, "Pizza Mussarela", "Pizza", 42.00);
                produto(produtoRepo, "Pizza Portuguesa", "Pizza", 48.50);
                produto(produtoRepo, "Refrigerante 2L", "Bebida", 12.00);
                produto(produtoRepo, "Esfiha de Carne", "Esfiha", 6.50);
                log.info("Produtos de exemplo criados.");
            }

            if (pedidosDemo && pedidoRepo.count() == 0) {
                criarPedidosDemo(pedidoRepo, produtoRepo.findAll());
            }
        };
    }

    private void criarSeAusente(UsuarioRepository repo, PasswordEncoder encoder, String username,
            String senha, Perfil perfil) {
        if (repo.findByUsername(username).isEmpty()) {
            Usuario u = new Usuario();
            u.setUsername(username);
            u.setPassword(encoder.encode(senha));
            u.setRole(perfil.name());
            repo.save(u);
            log.info("Usuário de demonstração '{}' ({}) criado.", username, perfil);
        }
    }

    private void criarPedidosDemo(PedidoRepository repo, List<Produto> todos) {
        List<Produto> produtos = todos.stream().filter(p -> p.getPreco() != null && p.getPreco() > 0).toList();
        if (produtos.isEmpty()) {
            return;
        }
        Random aleatorio = new Random(42);
        Instant agora = Instant.now();
        List<Pedido> pedidos = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            Pedido p = new Pedido();
            p.setClienteNome("Cliente demonstração " + (i + 1));
            p.setTipoEntrega(aleatorio.nextBoolean() ? TipoEntrega.ENTREGA : TipoEntrega.RETIRADA);
            p.setCriadoPor("atendente");
            p.setDataEntrada(agora.minus(Duration.ofMinutes(aleatorio.nextInt(30 * 24 * 60))));
            List<ItemPedido> itens = new ArrayList<>();
            for (int j = 0; j <= aleatorio.nextInt(3); j++) {
                Produto produto = produtos.get(aleatorio.nextInt(produtos.size()));
                itens.add(new ItemPedido(produto.getId(), produto.getNome(),
                        BigDecimal.valueOf(produto.getPreco()), 1 + aleatorio.nextInt(2)));
            }
            p.setItens(itens);
            if (aleatorio.nextInt(5) == 0) {
                p.setDescontoPercentual(BigDecimal.TEN);
            }
            p.recalcularTotais();
            int sorteio = aleatorio.nextInt(10);
            StatusPedido status = sorteio == 0 ? StatusPedido.CANCELADO
                    : sorteio == 1 ? StatusPedido.EM_PREPARACAO : StatusPedido.FINALIZADO;
            p.setStatus(status.name());
            if (status == StatusPedido.CANCELADO) {
                p.setCanceladoPor("gerente");
                p.setDataCancelamento(p.getDataEntrada().plus(Duration.ofMinutes(10)));
                p.setJustificativaCancelamento("Cliente desistiu");
            } else if (status == StatusPedido.FINALIZADO) {
                p.setDataSaida(p.getDataEntrada().plus(Duration.ofMinutes(40)));
                p.setSaidaPor("gerente");
                p.setDataFinalizacao(p.getDataEntrada().plus(Duration.ofMinutes(60)));
            }
            pedidos.add(p);
        }
        repo.saveAll(pedidos);
        log.info("Pedidos de demonstração criados para o dashboard.");
    }

    private void produto(ProdutoRepository repo, String nome, String tipo, double preco) {
        Produto p = new Produto();
        p.setNome(nome);
        p.setTipo(tipo);
        p.setPreco(preco);
        p.setAtivo(true);
        repo.save(p);
    }
}

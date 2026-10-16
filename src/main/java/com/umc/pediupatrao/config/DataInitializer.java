package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
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
    CommandLineRunner initDatabase(UsuarioRepository repo, ProdutoRepository produtoRepo, PasswordEncoder encoder,
            @Value("${app.seed.enabled:true}") boolean habilitado,
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

    private void produto(ProdutoRepository repo, String nome, String tipo, double preco) {
        Produto p = new Produto();
        p.setNome(nome);
        p.setTipo(tipo);
        p.setPreco(preco);
        p.setAtivo(true);
        repo.save(p);
    }
}

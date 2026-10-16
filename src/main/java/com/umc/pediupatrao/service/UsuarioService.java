package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.UsuarioRequest;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.security.ContextoUsuario;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioService {

    static final int SENHA_MINIMA = 8;
    static final int SENHA_MAXIMA = 72;
    private static final Pattern USERNAME_VALIDO = Pattern.compile("^[A-Za-z0-9._-]{3,50}$");

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    public Optional<Usuario> buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username);
    }

    public Iterable<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    public Usuario criar(UsuarioRequest dados) {
        String username = validarUsername(dados.username());
        Perfil perfil = validarPerfil(dados.role());
        String senha = validarSenha(dados.password(), true);

        if (usuarioRepository.findByUsername(username).isPresent()) {
            throw new RegraNegocioException("Já existe um usuário com esse username.");
        }

        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPassword(passwordEncoder.encode(senha));
        usuario.setRole(perfil.name());
        return usuarioRepository.save(usuario);
    }

    public Usuario atualizar(String id, UsuarioRequest dados) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        String atual = ContextoUsuario.username();
        boolean ehOProprio = existente.getUsername().equals(atual);

        String novoUsername = validarUsername(dados.username());
        Perfil novoPerfil = validarPerfil(dados.role());
        String novaSenha = validarSenha(dados.password(), false);

        if (ehOProprio && !novoUsername.equals(existente.getUsername())) {
            throw new RegraNegocioException("Você não pode alterar o seu próprio username.");
        }
        Perfil perfilAtual = Perfil.de(existente.getRole()).orElse(null);
        boolean mudaPerfil = perfilAtual != novoPerfil;
        if (ehOProprio && mudaPerfil) {
            throw new RegraNegocioException("Você não pode alterar o seu próprio perfil.");
        }
        if (perfilAtual == Perfil.ADMIN && novoPerfil != Perfil.ADMIN
                && usuarioRepository.countByRole(Perfil.ADMIN.name()) <= 1) {
            throw new RegraNegocioException("O sistema precisa manter ao menos um ADMIN.");
        }
        if (!novoUsername.equals(existente.getUsername())
                && usuarioRepository.findByUsername(novoUsername).isPresent()) {
            throw new RegraNegocioException("Já existe um usuário com esse username.");
        }

        existente.setUsername(novoUsername);
        existente.setRole(novoPerfil.name());
        if (novaSenha != null) {
            existente.setPassword(passwordEncoder.encode(novaSenha));
        }
        return usuarioRepository.save(existente);
    }

    public void deletar(String id) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        if (existente.getUsername().equals(ContextoUsuario.username())) {
            throw new RegraNegocioException("Você não pode excluir o seu próprio usuário.");
        }
        if (Perfil.ADMIN.name().equals(existente.getRole())
                && usuarioRepository.countByRole(Perfil.ADMIN.name()) <= 1) {
            throw new RegraNegocioException("O sistema precisa manter ao menos um ADMIN.");
        }
        usuarioRepository.deleteById(id);
    }

    private static String validarUsername(String username) {
        String u = username == null ? "" : username.trim();
        if (!USERNAME_VALIDO.matcher(u).matches()) {
            throw new RegraNegocioException(
                    "O username deve ter de 3 a 50 caracteres (letras, números, ponto, hífen ou sublinhado).");
        }
        return u;
    }

    private static Perfil validarPerfil(String role) {
        return Perfil.de(role).orElseThrow(() ->
                new RegraNegocioException("Perfil inválido. Use ADMIN, GERENTE ou ATENDENTE."));
    }

    private static String validarSenha(String senha, boolean obrigatoria) {
        if (senha == null || senha.isBlank()) {
            if (obrigatoria) {
                throw new RegraNegocioException("A senha é obrigatória.");
            }
            return null;
        }
        if (senha.length() < SENHA_MINIMA || senha.length() > SENHA_MAXIMA) {
            throw new RegraNegocioException("A senha deve ter entre " + SENHA_MINIMA + " e " + SENHA_MAXIMA + " caracteres.");
        }
        return senha;
    }
}

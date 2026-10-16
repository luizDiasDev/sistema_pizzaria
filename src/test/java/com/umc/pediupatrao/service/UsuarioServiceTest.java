package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.UsuarioRequest;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsuarioServiceTest {

    @Mock UsuarioRepository repository;

    PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    UsuarioService service;

    @BeforeEach
    void setUp() {
        service = new UsuarioService(repository, encoder);
        when(repository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId("novo");
            }
            return u;
        });
        TestSeguranca.autenticarComo("admin", "ADMIN");
    }

    @AfterEach
    void tearDown() {
        TestSeguranca.limpar();
    }

    private Usuario usuario(String id, String username, String role) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setUsername(username);
        u.setPassword("hash-antigo");
        u.setRole(role);
        when(repository.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    void criaUsuarioComSenhaCriptografada() {
        Usuario criado = service.criar(new UsuarioRequest("joana.silva", "senhaForte1", "ATENDENTE"));

        assertThat(criado.getRole()).isEqualTo("ATENDENTE");
        assertThat(criado.getPassword()).isNotEqualTo("senhaForte1");
        assertThat(encoder.matches("senhaForte1", criado.getPassword())).isTrue();
    }

    @Test
    void soAceitaOsTresPerfisDoSistema() {
        for (String invalido : new String[] {"USER", "SUPERADMIN", "", null, "ROLE_ADMIN"}) {
            assertThatThrownBy(() -> service.criar(new UsuarioRequest("fulano", "senhaForte1", invalido)))
                    .as("perfil %s", invalido)
                    .isInstanceOf(RegraNegocioException.class);
        }
        verify(repository, never()).save(any());
    }

    @Test
    void validaUsernameESenha() {
        assertThatThrownBy(() -> service.criar(new UsuarioRequest("a", "senhaForte1", "ADMIN")))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.criar(new UsuarioRequest("fulano", "curta", "ADMIN")))
                .isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> service.criar(new UsuarioRequest("fulano", null, "ADMIN")))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void naoPermiteUsernameDuplicado() {
        when(repository.findByUsername("joana")).thenReturn(Optional.of(new Usuario()));
        assertThatThrownBy(() -> service.criar(new UsuarioRequest("joana", "senhaForte1", "ATENDENTE")))
                .hasMessageContaining("Já existe");
    }

    @Test
    void ninguemAlteraOProprioPerfil() {
        usuario("u-admin", "admin", "ADMIN");
        when(repository.countByRole("ADMIN")).thenReturn(2L);

        assertThatThrownBy(() -> service.atualizar("u-admin", new UsuarioRequest("admin", null, "GERENTE")))
                .hasMessageContaining("próprio perfil");
        verify(repository, never()).save(any());
    }

    @Test
    void naoRebaixaOUltimoAdmin() {
        usuario("u-outro", "outro.admin", "ADMIN");
        when(repository.countByRole("ADMIN")).thenReturn(1L);

        assertThatThrownBy(() -> service.atualizar("u-outro", new UsuarioRequest("outro.admin", null, "ATENDENTE")))
                .hasMessageContaining("ao menos um ADMIN");
    }

    @Test
    void adminPodePromoverOutroUsuario() {
        usuario("u1", "joana", "ATENDENTE");

        Usuario salvo = service.atualizar("u1", new UsuarioRequest("joana", null, "GERENTE"));

        assertThat(salvo.getRole()).isEqualTo("GERENTE");
        assertThat(salvo.getPassword()).isEqualTo("hash-antigo");
    }

    @Test
    void trocaDeSenhaGravaSomenteOHash() {
        usuario("u1", "joana", "ATENDENTE");

        Usuario salvo = service.atualizar("u1", new UsuarioRequest("joana", "novaSenha123", "ATENDENTE"));

        assertThat(salvo.getPassword()).isNotEqualTo("novaSenha123");
        assertThat(encoder.matches("novaSenha123", salvo.getPassword())).isTrue();
    }

    @Test
    void naoExcluiASiMesmoNemOUltimoAdmin() {
        usuario("u-admin", "admin", "ADMIN");
        assertThatThrownBy(() -> service.deletar("u-admin")).hasMessageContaining("próprio usuário");

        usuario("u-outro", "outro.admin", "ADMIN");
        when(repository.countByRole("ADMIN")).thenReturn(1L);
        assertThatThrownBy(() -> service.deletar("u-outro")).hasMessageContaining("ao menos um ADMIN");
        verify(repository, never()).deleteById(any());
    }

    @Test
    void excluiOutroUsuario() {
        usuario("u1", "joana", "ATENDENTE");
        service.deletar("u1");
        verify(repository).deleteById("u1");
    }
}

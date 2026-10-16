package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClienteServiceTest {

    @Mock ClienteRepository clienteRepository;
    @Mock AuditoriaService auditoriaService;
    @Captor ArgumentCaptor<Map<String, String>> antesCaptor;
    @Captor ArgumentCaptor<Map<String, String>> depoisCaptor;

    ClienteService service;

    @BeforeEach
    void setUp() {
        service = new ClienteService(clienteRepository, auditoriaService);
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> {
            Cliente c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId("c1");
            }
            return c;
        });
        TestSeguranca.autenticarComo("atendente01", "ATENDENTE");
    }

    @AfterEach
    void tearDown() {
        TestSeguranca.limpar();
    }

    private Cliente existente() {
        Cliente c = new Cliente();
        c.setId("c1");
        c.setNome("Maria");
        c.setTelefone("(11) 99999-1234");
        c.setLogradouro("Rua das Flores");
        c.setNumero("10");
        c.setCidade("Mogi das Cruzes");
        c.setEndereco("endereco antigo");
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(c));
        return c;
    }

    private Cliente formulario() {
        Cliente c = new Cliente();
        c.setId("c1");
        c.setNome("Maria");
        c.setTelefone("(11) 99999-1234");
        c.setLogradouro("Rua das Flores");
        c.setNumero("10");
        c.setCidade("Mogi das Cruzes");
        return c;
    }

    @Test
    void alteracaoDeLogradouroGeraAuditoriaComValorAnteriorENovo() {
        existente();
        Cliente alterado = formulario();
        alterado.setLogradouro("Av. Brasil");

        service.salvar(alterado);

        verify(auditoriaService).registrar(eq(TipoOperacao.ALTERACAO_CLIENTE), eq("CLIENTE"), eq("c1"),
                antesCaptor.capture(), depoisCaptor.capture(), isNull());
        assertThat(antesCaptor.getValue()).containsEntry("logradouro", "Rua das Flores");
        assertThat(depoisCaptor.getValue()).containsEntry("logradouro", "Av. Brasil");
    }

    @Test
    void alteracaoDeTelefoneTambemEhAuditada() {
        existente();
        Cliente alterado = formulario();
        alterado.setTelefone("(11) 98888-0000");

        service.salvar(alterado);

        verify(auditoriaService).registrar(eq(TipoOperacao.ALTERACAO_CLIENTE), eq("CLIENTE"), eq("c1"),
                antesCaptor.capture(), depoisCaptor.capture(), isNull());
        assertThat(antesCaptor.getValue()).containsEntry("telefone", "(11) 99999-1234");
        assertThat(depoisCaptor.getValue()).containsEntry("telefone", "(11) 98888-0000");
    }

    @Test
    void campoAntigoEnderecoEhPreservadoQuandoOFormularioNaoOEnvia() {
        Cliente banco = existente();
        service.salvar(formulario());
        assertThat(banco.getEndereco()).isEqualTo("endereco antigo");
    }

    @Test
    void textosVaziosViramNuloEEspacosSaoRemovidos() {
        existente();
        Cliente alterado = formulario();
        alterado.setComplemento("   ");
        alterado.setNome("  Maria  ");

        Cliente salvo = service.salvar(alterado);

        assertThat(salvo.getComplemento()).isNull();
        assertThat(salvo.getNome()).isEqualTo("Maria");
        verify(auditoriaService).registrar(eq(TipoOperacao.ALTERACAO_CLIENTE), eq("CLIENTE"), eq("c1"),
                antesCaptor.capture(), depoisCaptor.capture(), isNull());
        assertThat(antesCaptor.getValue()).isEqualTo(depoisCaptor.getValue());
    }

    @Test
    void criacaoEhAuditada() {
        Cliente novo = new Cliente();
        novo.setNome("João");
        novo.setTelefone("(11) 91111-2222");

        Cliente salvo = service.salvar(novo);

        assertThat(salvo.getId()).isEqualTo("c1");
        verify(auditoriaService).registrar(eq(TipoOperacao.CRIACAO_CLIENTE), eq("CLIENTE"), eq("c1"),
                any(), depoisCaptor.capture(), isNull());
        assertThat(depoisCaptor.getValue()).containsEntry("nome", "João");
    }

    @Test
    void exigeNomeETelefone() {
        Cliente semNome = new Cliente();
        semNome.setTelefone("1");
        assertThatThrownBy(() -> service.salvar(semNome)).isInstanceOf(RegraNegocioException.class);

        Cliente semTelefone = new Cliente();
        semTelefone.setNome("A");
        assertThatThrownBy(() -> service.salvar(semTelefone)).isInstanceOf(RegraNegocioException.class);
        verify(clienteRepository, never()).save(any());
    }

    @Test
    void atualizacaoDeClienteInexistenteRetornaNaoEncontrado() {
        Cliente c = formulario();
        c.setId("nao-existe");
        assertThatThrownBy(() -> service.salvar(c)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void exclusaoEhAuditada() {
        TestSeguranca.autenticarComo("gerente01", "GERENTE");
        existente();

        service.excluir("c1");

        verify(clienteRepository).deleteById("c1");
        verify(auditoriaService).registrar(eq(TipoOperacao.EXCLUSAO_CLIENTE), eq("CLIENTE"), eq("c1"),
                antesCaptor.capture(), eq(Map.of()), isNull());
        assertThat(antesCaptor.getValue()).containsEntry("nome", "Maria");
    }
}

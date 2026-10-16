package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.dto.AuditoriaView;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.AuditoriaEvento;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.repository.CrudRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditoriaServiceTest {

    private static final Instant AGORA = Instant.parse("2026-09-23T17:35:00Z");

    @Mock AuditoriaRepository repository;

    AuditoriaService service;

    @BeforeEach
    void setUp() {
        service = new AuditoriaService(repository, Clock.fixed(AGORA, ZoneOffset.UTC));
        when(repository.save(any(AuditoriaEvento.class))).thenAnswer(inv -> inv.getArgument(0));
        TestSeguranca.autenticarComo("atendente01", "ATENDENTE");
    }

    @AfterEach
    void tearDown() {
        TestSeguranca.limpar();
    }

    private static Map<String, String> mapa(String... paresChaveValor) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < paresChaveValor.length; i += 2) {
            m.put(paresChaveValor[i], paresChaveValor[i + 1]);
        }
        return m;
    }

    @Test
    void registraSomenteOsCamposModificadosComUsuarioPerfilEDataHora() {
        Map<String, String> antes = mapa("logradouro", "Rua das Flores", "cidade", "Mogi das Cruzes");
        Map<String, String> depois = mapa("logradouro", "Av. Brasil", "cidade", "Mogi das Cruzes");

        Optional<AuditoriaEvento> resultado = service.registrar(
                TipoOperacao.ALTERACAO_CLIENTE, "CLIENTE", "c1", antes, depois, null);

        assertThat(resultado).isPresent();
        AuditoriaEvento e = resultado.get();
        assertThat(e.getUsuario()).isEqualTo("atendente01");
        assertThat(e.getPerfil()).isEqualTo("ATENDENTE");
        assertThat(e.getDataHora()).isEqualTo(AGORA);
        assertThat(e.getOperacao()).isEqualTo(TipoOperacao.ALTERACAO_CLIENTE);
        assertThat(e.getEntidade()).isEqualTo("CLIENTE");
        assertThat(e.getEntidadeId()).isEqualTo("c1");
        assertThat(e.getAlteracoes()).containsExactly(new AlteracaoCampo("logradouro", "Rua das Flores", "Av. Brasil"));
        assertThat(e.getJustificativa()).isNull();
    }

    @Test
    void naoRegistraQuandoNadaMudouESemJustificativa() {
        Map<String, String> igual = mapa("nome", "Maria");
        assertThat(service.registrar(TipoOperacao.ALTERACAO_CLIENTE, "CLIENTE", "c1", igual, igual, null)).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void registraJustificativaMesmoSemCamposAlterados() {
        Optional<AuditoriaEvento> r = service.registrar(TipoOperacao.CANCELAMENTO_PEDIDO, "PEDIDO", "p1",
                Map.of(), Map.of(), "  Cliente desistiu ");
        assertThat(r).isPresent();
        assertThat(r.get().getJustificativa()).isEqualTo("Cliente desistiu");
    }

    @Test
    void criacaoRegistraOsValoresNovosComAnteriorNulo() {
        Optional<AuditoriaEvento> r = service.registrar(TipoOperacao.CRIACAO_CLIENTE, "CLIENTE", "c1",
                Map.of(), mapa("nome", "Maria", "telefone", "(11) 99999-1234"), null);

        assertThat(r.get().getAlteracoes()).containsExactly(
                new AlteracaoCampo("nome", null, "Maria"),
                new AlteracaoCampo("telefone", null, "(11) 99999-1234"));
    }

    @Test
    void exclusaoRegistraOsValoresAnterioresComNovoNulo() {
        Optional<AuditoriaEvento> r = service.registrar(TipoOperacao.EXCLUSAO_CLIENTE, "CLIENTE", "c1",
                mapa("nome", "Maria"), Map.of(), null);
        assertThat(r.get().getAlteracoes()).containsExactly(new AlteracaoCampo("nome", "Maria", null));
    }

    @Test
    void semUsuarioAutenticadoRegistraComoSistema() {
        TestSeguranca.limpar();
        AuditoriaEvento e = service.registrar(TipoOperacao.CRIACAO_CLIENTE, "CLIENTE", "c1",
                Map.of(), mapa("nome", "X"), null).get();
        assertThat(e.getUsuario()).isEqualTo("sistema");
    }

    @Test
    void salvaOEventoNoRepositorio() {
        service.registrar(TipoOperacao.CRIACAO_CLIENTE, "CLIENTE", "c1", Map.of(), mapa("nome", "X"), null);
        ArgumentCaptor<AuditoriaEvento> captor = ArgumentCaptor.forClass(AuditoriaEvento.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getId()).isNull();
    }

    @Test
    void telefoneApareceMascaradoNaConsulta() {
        assertThat(AuditoriaService.mascarar("telefone", "(11) 99999-1234")).isEqualTo("***********1234");
        assertThat(AuditoriaService.mascarar("telefone", "123")).isEqualTo("****");
        assertThat(AuditoriaService.mascarar("telefone", null)).isNull();
        assertThat(AuditoriaService.mascarar("logradouro", "Rua das Flores")).isEqualTo("Rua das Flores");

        AuditoriaEvento e = new AuditoriaEvento(AGORA, "atendente01", "ATENDENTE", TipoOperacao.ALTERACAO_CLIENTE,
                "CLIENTE", "c1", List.of(new AlteracaoCampo("telefone", "(11) 98888-0000", "(11) 97777-1111")), null);
        AuditoriaView view = AuditoriaService.paraView(e);
        assertThat(view.alteracoes().get(0).valorAnterior()).endsWith("0000").doesNotContain("98888");
        assertThat(view.alteracoes().get(0).valorNovo()).endsWith("1111").doesNotContain("97777");
    }

    @Test
    void repositorioNaoOfereceAtualizacaoNemExclusao() {
        assertThat(CrudRepository.class.isAssignableFrom(AuditoriaRepository.class)).isFalse();
        for (Method m : AuditoriaRepository.class.getMethods()) {
            assertThat(m.getName()).doesNotStartWith("delete").doesNotStartWith("remove").doesNotStartWith("update");
        }
    }

    @Test
    void eventoEhImutavel() {
        boolean temSetter = Arrays.stream(AuditoriaEvento.class.getMethods())
                .anyMatch(m -> m.getName().startsWith("set"));
        assertThat(temSetter).isFalse();
    }
}

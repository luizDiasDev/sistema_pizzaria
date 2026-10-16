package com.umc.pediupatrao.service;

import com.umc.pediupatrao.dto.AuditoriaView;
import com.umc.pediupatrao.entity.AlteracaoCampo;
import com.umc.pediupatrao.entity.AuditoriaEvento;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.repository.AuditoriaRepository;
import com.umc.pediupatrao.security.ContextoUsuario;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    private static final Set<String> CAMPOS_MASCARADOS = Set.of("telefone");

    private final AuditoriaRepository auditoriaRepository;
    private final Clock clock;

    public AuditoriaService(AuditoriaRepository auditoriaRepository, Clock clock) {
        this.auditoriaRepository = auditoriaRepository;
        this.clock = clock;
    }

    public Optional<AuditoriaEvento> registrar(TipoOperacao operacao, String entidade, String entidadeId,
            Map<String, String> antes, Map<String, String> depois, String justificativa) {

        List<AlteracaoCampo> alteracoes = diferencas(antes, depois);
        boolean temJustificativa = justificativa != null && !justificativa.isBlank();
        if (alteracoes.isEmpty() && !temJustificativa) {
            return Optional.empty();
        }

        AuditoriaEvento evento = new AuditoriaEvento(
                Instant.now(clock),
                ContextoUsuario.username(),
                ContextoUsuario.perfil(),
                operacao,
                entidade,
                entidadeId,
                alteracoes,
                temJustificativa ? justificativa.trim() : null);

        AuditoriaEvento salvo = auditoriaRepository.save(evento);
        log.info("AUDITORIA operacao={} entidade={} id={} usuario={}", operacao, entidade, entidadeId, evento.getUsuario());
        return Optional.of(salvo);
    }

    static List<AlteracaoCampo> diferencas(Map<String, String> antes, Map<String, String> depois) {
        Map<String, String> a = antes == null ? Map.of() : antes;
        Map<String, String> d = depois == null ? Map.of() : depois;
        Set<String> campos = new LinkedHashSet<>(a.keySet());
        campos.addAll(d.keySet());

        List<AlteracaoCampo> resultado = new ArrayList<>();
        for (String campo : campos) {
            String valorAnterior = a.get(campo);
            String valorNovo = d.get(campo);
            if (!Objects.equals(valorAnterior, valorNovo)) {
                resultado.add(new AlteracaoCampo(campo, valorAnterior, valorNovo));
            }
        }
        return resultado;
    }

    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public List<AuditoriaView> listar() {
        return auditoriaRepository.findAllByOrderByDataHoraDesc().stream()
                .map(AuditoriaService::paraView)
                .toList();
    }

    static AuditoriaView paraView(AuditoriaEvento e) {
        List<AuditoriaView.Alteracao> alteracoes = e.getAlteracoes().stream()
                .map(a -> new AuditoriaView.Alteracao(
                        a.campo(),
                        mascarar(a.campo(), a.valorAnterior()),
                        mascarar(a.campo(), a.valorNovo())))
                .toList();
        return new AuditoriaView(e.getId(), e.getDataHora(), e.getUsuario(), e.getPerfil(),
                e.getOperacao() == null ? null : e.getOperacao().name(),
                e.getOperacao() == null ? null : e.getOperacao().getDescricao(),
                e.getEntidade(), e.getEntidadeId(), alteracoes, e.getJustificativa());
    }

    static String mascarar(String campo, String valor) {
        if (valor == null || !CAMPOS_MASCARADOS.contains(campo)) {
            return valor;
        }
        if (valor.length() <= 4) {
            return "****";
        }
        return "*".repeat(valor.length() - 4) + valor.substring(valor.length() - 4);
    }
}

package com.umc.pediupatrao.dto;

import java.time.Instant;
import java.util.List;

public record AuditoriaView(
        String id,
        Instant dataHora,
        String usuario,
        String perfil,
        String operacao,
        String operacaoDescricao,
        String entidade,
        String entidadeId,
        List<Alteracao> alteracoes,
        String justificativa) {

    public record Alteracao(String campo, String valorAnterior, String valorNovo) {
    }
}

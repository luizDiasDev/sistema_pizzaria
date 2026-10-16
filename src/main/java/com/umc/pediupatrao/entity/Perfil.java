package com.umc.pediupatrao.entity;

import java.util.Locale;
import java.util.Optional;

public enum Perfil {
    ADMIN,
    GERENTE,
    ATENDENTE;

    public String authority() {
        return "ROLE_" + name();
    }

    public static Optional<Perfil> de(String valor) {
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(valor.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}

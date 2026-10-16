package com.umc.pediupatrao.service;

import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

final class TestSeguranca {

    private TestSeguranca() {
    }

    static void autenticarComo(String username, String perfil) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(username, "n/a", "ROLE_" + perfil));
    }

    static void limpar() {
        SecurityContextHolder.clearContext();
    }
}

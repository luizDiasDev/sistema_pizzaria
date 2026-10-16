package com.umc.pediupatrao.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public final class ContextoUsuario {

    public static final String SISTEMA = "sistema";

    private ContextoUsuario() {
    }

    private static Authentication autenticacao() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || a instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return a;
    }

    public static String username() {
        Authentication a = autenticacao();
        return a == null ? SISTEMA : a.getName();
    }

    public static String perfil() {
        Authentication a = autenticacao();
        if (a == null) {
            return "SISTEMA";
        }
        return a.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(s -> s.startsWith("ROLE_"))
                .map(s -> s.substring("ROLE_".length()))
                .findFirst()
                .orElse("SEM_PERFIL");
    }
}

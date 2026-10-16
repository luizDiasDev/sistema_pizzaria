package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.Perfil;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Disponibiliza o perfil do usuário logado para as telas (apenas para exibir/ocultar
 * itens). Isto é conveniência visual: a autorização real está no backend.
 */
@ControllerAdvice(annotations = Controller.class)
public class PerfilModelAdvice {

    @ModelAttribute
    public void perfilDoUsuario(Model model, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }
        model.addAttribute("usuarioLogado", authentication.getName());
        model.addAttribute("ehAdmin", tem(authentication, Perfil.ADMIN));
        model.addAttribute("ehGerente", tem(authentication, Perfil.GERENTE));
        model.addAttribute("ehAtendente", tem(authentication, Perfil.ATENDENTE));
        model.addAttribute("perfilLogado", authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst().orElse("SEM PERFIL"));
    }

    private static boolean tem(Authentication auth, Perfil perfil) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(perfil.authority()));
    }
}

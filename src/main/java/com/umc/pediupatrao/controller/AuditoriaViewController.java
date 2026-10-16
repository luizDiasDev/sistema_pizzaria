package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.service.AuditoriaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuditoriaViewController {

    private final AuditoriaService auditoriaService;

    public AuditoriaViewController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/auditoria")
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public String consultar(Model model) {
        model.addAttribute("eventos", auditoriaService.listar());
        model.addAttribute("content", "auditoria/lista :: content");
        return "layout";
    }
}

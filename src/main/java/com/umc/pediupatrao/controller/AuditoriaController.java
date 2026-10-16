package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.AuditoriaView;
import com.umc.pediupatrao.service.AuditoriaService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public List<AuditoriaView> consultar() {
        return auditoriaService.listar();
    }
}

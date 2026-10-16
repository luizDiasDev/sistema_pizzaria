package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.UsuarioRequest;
import com.umc.pediupatrao.dto.UsuarioResponse;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.service.UsuarioService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/usuarios")
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    public ResponseEntity<List<UsuarioResponse>> listarTodos() {
        List<UsuarioResponse> lista = new ArrayList<>();
        for (Usuario u : usuarioService.listarTodos()) {
            lista.add(UsuarioResponse.de(u));
        }
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponse> buscarPorId(@PathVariable String id) {
        return usuarioService.buscarPorId(id)
                .map(u -> ResponseEntity.ok(UsuarioResponse.de(u)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> criarUsuario(@RequestBody UsuarioRequest dados) {
        Usuario criado = usuarioService.criar(dados);
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponse.de(criado));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponse> atualizarUsuario(@PathVariable String id, @RequestBody UsuarioRequest dados) {
        return ResponseEntity.ok(UsuarioResponse.de(usuarioService.atualizar(id, dados)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarUsuario(@PathVariable String id) {
        usuarioService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}

package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ClienteService {

    static final String ENTIDADE = "CLIENTE";

    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoriaService;

    public ClienteService(ClienteRepository clienteRepository, AuditoriaService auditoriaService) {
        this.clienteRepository = clienteRepository;
        this.auditoriaService = auditoriaService;
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    @PreAuthorize("hasRole('GERENTE')")
    public void excluir(String id) {
        Cliente existente = clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado."));
        Map<String, String> antes = snapshot(existente);
        clienteRepository.deleteById(id);
        auditoriaService.registrar(TipoOperacao.EXCLUSAO_CLIENTE, ENTIDADE, id, antes, Map.of(), null);
    }

    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente salvar(Cliente entrada) {
        normalizar(entrada);
        if (entrada.getNome() == null) {
            throw new RegraNegocioException("O nome do cliente é obrigatório.");
        }
        if (entrada.getTelefone() == null) {
            throw new RegraNegocioException("O telefone do cliente é obrigatório.");
        }

        if (entrada.getId() == null) {
            Cliente salvo = clienteRepository.save(entrada);
            auditoriaService.registrar(TipoOperacao.CRIACAO_CLIENTE, ENTIDADE, salvo.getId(),
                    Map.of(), snapshot(salvo), null);
            return salvo;
        }

        Cliente existente = clienteRepository.findById(entrada.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado para atualização."));
        Map<String, String> antes = snapshot(existente);

        existente.setNome(entrada.getNome());
        existente.setTelefone(entrada.getTelefone());
        existente.setCep(entrada.getCep());
        existente.setLogradouro(entrada.getLogradouro());
        existente.setNumero(entrada.getNumero());
        existente.setComplemento(entrada.getComplemento());
        existente.setBairro(entrada.getBairro());
        existente.setCidade(entrada.getCidade());
        existente.setEstado(entrada.getEstado());
        if (entrada.getEndereco() != null) {
            existente.setEndereco(entrada.getEndereco());
        }

        Map<String, String> depois = snapshot(existente);
        Cliente salvo = clienteRepository.save(existente);
        auditoriaService.registrar(TipoOperacao.ALTERACAO_CLIENTE, ENTIDADE, salvo.getId(), antes, depois, null);
        return salvo;
    }

    private static void normalizar(Cliente c) {
        c.setId(vazioParaNull(c.getId()));
        c.setNome(vazioParaNull(c.getNome()));
        c.setTelefone(vazioParaNull(c.getTelefone()));
        c.setCep(vazioParaNull(c.getCep()));
        c.setLogradouro(vazioParaNull(c.getLogradouro()));
        c.setNumero(vazioParaNull(c.getNumero()));
        c.setComplemento(vazioParaNull(c.getComplemento()));
        c.setBairro(vazioParaNull(c.getBairro()));
        c.setCidade(vazioParaNull(c.getCidade()));
        c.setEstado(vazioParaNull(c.getEstado()));
        c.setEndereco(vazioParaNull(c.getEndereco()));
    }

    private static String vazioParaNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    static Map<String, String> snapshot(Cliente c) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("nome", c.getNome());
        m.put("telefone", c.getTelefone());
        m.put("cep", c.getCep());
        m.put("logradouro", c.getLogradouro());
        m.put("numero", c.getNumero());
        m.put("complemento", c.getComplemento());
        m.put("bairro", c.getBairro());
        m.put("cidade", c.getCidade());
        m.put("estado", c.getEstado());
        m.put("endereco", c.getEndereco());
        return m;
    }
}

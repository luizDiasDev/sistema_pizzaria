# PediuPatrão - Evolução do Sistema de Pedidos (M1 - Parte 2)

Sistema de pedidos de pizzaria em Java 21, Spring Boot 3.4, Spring Security, MongoDB e Thymeleaf.
Esta versão adiciona perfis de acesso, fluxo controlado de pedidos, desconto e cancelamento exclusivos do gerente e auditoria persistente.

**Aluno:** Luiz Eduardo Dias
**RGM:** 11231104074

## Como executar

Requisitos: Java 21 e MongoDB rodando em `localhost:27017`.

```bash
./mvnw spring-boot:run       # Windows: .\mvnw.cmd spring-boot:run
./mvnw test                  # testes automatizados
```

Acesse http://localhost:8080. Para outro MongoDB, defina a variável `MONGODB_URI`.

### Usuários de demonstração

Criados na primeira execução, junto com alguns produtos de exemplo. Para desligar: `APP_SEED_ENABLED=false`.

| Usuário | Senha | Perfil |
|---|---|---|
| `admin` | `teste123` | ADMIN |
| `gerente` | `gerente123` | GERENTE |
| `atendente` | `atendente123` | ATENDENTE |

## Requisitos implementados

### RF01 - Usuários e permissões
- Perfis ADMIN, GERENTE e ATENDENTE, carregados do banco no login.
- Autorização no backend em duas camadas: regras por URL no `SecurityConfig` e `@PreAuthorize` nos controllers e services.
- Proteção contra autoelevação: só o ADMIN gerencia usuários, ninguém altera o próprio perfil e o último ADMIN não pode ser rebaixado nem excluído.
- A API de usuários deixou de ser pública e não expõe mais a senha.
- CSRF habilitado. Sem login, a API responde 401; sem permissão, 403.

| Operação | ADMIN | GERENTE | ATENDENTE |
|---|:-:|:-:|:-:|
| Gerenciar usuários | ✅ | ❌ | ❌ |
| Consultar pedidos | ✅ | ✅ | ✅ |
| Registrar pedido | ❌ | ✅ | ✅ |
| Avançar status (inclui saída) | ❌ | ✅ | ❌ |
| Aplicar desconto | ❌ | ✅ | ❌ |
| Cancelar pedido | ❌ | ✅ | ❌ |
| Cadastrar / alterar clientes | ❌ | ❌ | ✅ |
| Excluir clientes | ❌ | ✅ | ❌ |
| Consultar auditoria | ✅ | ✅ | ❌ |

### RF02 - Pedido
- O pedido guarda cliente, forma de entrega, itens, desconto, total, responsável e data/hora de entrada.
- Preços e total são calculados no servidor a partir do cadastro de produtos; valores enviados pela requisição são ignorados.

### RF03 - Fluxo de status
`RECEBIDO → EM_PREPARACAO → PRONTO → SAIU_PARA_ENTREGA ou RETIRADO → FINALIZADO`, além de `CANCELADO`.
- Transições fora dessa ordem são rejeitadas (409).
- Na saída, o sistema grava a data/hora e o responsável.
- Pedidos cancelados ou finalizados não aceitam mais alterações.

### RF04 - Desconto e cancelamento (somente GERENTE)
- Desconto de até 20%, apenas antes da saída; o total é recalculado.
- Cancelamento com justificativa obrigatória, apenas antes da saída.

### RF05 - Auditoria
- Coleção `auditoria` no MongoDB. O repositório não tem update nem delete, e não existe rota para alterar registros.
- Cada registro guarda usuário, perfil, data/hora, operação, entidade, campos alterados (valor anterior e novo) e justificativa quando exigida.
- São auditados: pedidos (criação, status, desconto, cancelamento) e clientes (criação, alteração, exclusão).
- Consulta pela tela **Auditoria** ou `GET /api/auditoria`, somente ADMIN e GERENTE. Telefones aparecem mascarados e senhas nunca são registradas.

## Testes automatizados
- `StatusPedidoTest`, `PedidoTest`, `PedidoServiceTest`: fluxo, transições inválidas, saída, desconto, cancelamento e cálculo do total.
- `AuditoriaServiceTest`, `ClienteServiceTest`: campos alterados, usuário/perfil e mascaramento.
- `UsuarioServiceTest`: autoelevação e último ADMIN.
- `AutorizacaoApiTest`, `AutorizacaoTelasTest`: respostas 401/403 por perfil e rota.

## Demonstração (vídeo)
1. **Atendente:** cadastra e altera um cliente e registra dois pedidos (entrega e retirada).
2. **Gerente:** aplica desconto, avança um pedido até a saída (data/hora e responsável registrados) e cancela o outro com justificativa.
3. **Postman, como atendente:** chamando a API direto, desconto, cancelamento e auditoria respondem 403.
4. **Admin:** consulta os pedidos sem as ações do gerente e abre a auditoria com todas as operações registradas.

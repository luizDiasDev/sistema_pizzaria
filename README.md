# PediuPatrão 🍕 - Evolução do Sistema de Pedidos (M1 · Parte 2)

Sistema de pedidos de pizzaria em **Java 21 · Spring Boot 3.4 · Spring Security · MongoDB · Thymeleaf**.
Esta versão adiciona **perfis de acesso**, **fluxo controlado de pedidos**, **descontos/cancelamentos exclusivos do gerente** e **auditoria persistente**.

Aluno: Luiz Eduardo Dias
RGM: 11231104074

## Como executar

Requisitos: Java 21, MongoDB rodando em `localhost:27017` (ex.: MongoDB Community Server) e Maven (ou o `mvnw` do projeto).

```bash
./mvnw spring-boot:run            # Windows: .\mvnw.cmd spring-boot:run
```

Para usar outro MongoDB, defina a variável de ambiente `MONGODB_URI` (nenhuma credencial fica gravada no código).
Acesse http://localhost:8080
Testes: `./mvnw test`.

### Usuários de demonstração (criados na primeira execução)

| Usuário | Senha | Perfil |
|---|---|---|
| `admin` | `teste123` | ADMIN |
| `gerente` | `gerente123` | GERENTE |
| `atendente` | `atendente123` | ATENDENTE |

São **apenas para demonstração local** (para desligar: `APP_SEED_ENABLED=false`; para trocar: `app.seed.*-senha`).
Também são criados alguns produtos de exemplo se não houver nenhum.
Usuários do modelo antigo (sem perfil ou `USER`) são migrados para ATENDENTE, o `admin` para ADMIN.

## Requisitos implementados

### RF01 - Usuários e permissões
- Perfis **ADMIN**, **GERENTE** e **ATENDENTE** (`entity/Perfil`). O `UsuarioDetailsService` agora atribui a role **real** gravada no banco (antes era sempre `USER`); perfil desconhecido = sem nenhuma permissão.
- Autorização **no backend**, em duas camadas: regras por URL em `SecurityConfig` (qualquer rota não listada exige login) e `@PreAuthorize` em controladores **e** serviços.
- A API `/api/usuarios`, que era pública (`permitAll`), agora é exclusiva do ADMIN, e **não devolve mais a senha/hash** (`UsuarioResponse`).
- Proteção contra autoelevação (`UsuarioService`): só ADMIN cria/altera usuários; só os 3 perfis são aceitos; ninguém altera o **próprio** perfil/username nem se exclui; o **último ADMIN** não pode ser rebaixado/excluído; os formulários usam DTO (`UsuarioRequest`), então `id`/hash nunca são vinculados pela requisição.
- **CSRF habilitado** (antes desativado): formulários Thymeleaf recebem o token automaticamente; `fetch` envia o cabeçalho lido das meta tags do layout (`apiJson` em `static/js/script.js`). Logout agora é POST.
- Tentativas indevidas: APIs respondem **401** (sem login) ou **403** em JSON; cada negação é registrada em log (`ACESSO NEGADO usuario=… uri=…`).

### Matriz de permissões

| Operação | ADMIN | GERENTE | ATENDENTE |
|---|:-:|:-:|:-:|
| Gerir usuários e perfis | ✅ | ❌ | ❌ |
| Consultar pedidos | ✅ | ✅ | ✅ |
| Registrar / alterar itens de pedido (só em RECEBIDO) | ❌ | ✅ | ✅ |
| Avançar status (incl. saída/retirada) | ❌ | ✅ | ❌ |
| **Aplicar desconto** | ❌ | ✅ | ❌ |
| **Cancelar pedido** | ❌ | ✅ | ❌ |
| Consultar clientes | ❌ | ✅ | ✅ |
| Cadastrar / atualizar clientes | ❌ | ❌ | ✅ |
| Excluir clientes (auditado) | ❌ | ✅ | ❌ |
| Consultar auditoria | ✅ | ✅ | ❌ |

> Decisões onde o enunciado é silencioso: ATENDENTE apenas *acompanha* o andamento (quem avança o status é o GERENTE); a exclusão de cliente ficou com o GERENTE. Para mudar, basta ajustar os `@PreAuthorize`.

### RF02 - Pedido
`Pedido` guarda cliente (id + nome), forma de entrega, **itens** (produto, nome, preço unitário, quantidade, subtotal), desconto %, valor do desconto, **total**, responsável (`criadoPor`), **data/hora de entrada**, saída, finalização e cancelamento.
O cliente envia só `clienteId`, `tipoEntrega` (ENTREGA/RETIRADA) e `{produtoId, quantidade}` (`PedidoRequest`). **Preços e total são calculados no servidor** a partir do cadastro de produtos (`Pedido.recalcularTotais`); campos como `total`/`status` enviados na requisição são ignorados. Validações: itens obrigatórios, quantidade 1–99, produto existente/ativo/com preço, cliente existente.

### RF03 - Fluxo e transições (`StatusPedido`)
`RECEBIDO → EM_PREPARACAO → PRONTO → SAIU_PARA_ENTREGA (entrega) | RETIRADO (retirada) → FINALIZADO`.
- Transições inválidas (pular etapa, voltar, finalizar em preparação…) retornam **409**.
- Na saída, **data/hora e responsável são gravados automaticamente** pelo servidor.
- `CANCELADO` não passa pelo endpoint de status; pedidos cancelados/finalizados não aceitam mais nenhuma alteração.

### RF04 - Descontos e cancelamentos (somente GERENTE)
- **Desconto** percentual, `0 < p ≤ 20`, só **antes da saída**; substitui o anterior (não acumula) e recalcula o total.
- **Cancelamento** com justificativa obrigatória (≥ 5 caracteres), só **antes da saída**; preserva todos os dados e bloqueia alterações posteriores.
- Ambos são auditados com valores anteriores/novos.

### RF05 - Auditoria
- Coleção `auditoria` (MongoDB): `AuditoriaEvento` (imutável), `AuditoriaRepository` (**sem** update/delete: não estende `CrudRepository`) e `AuditoriaService`.
- Cada evento: **usuário e perfil** (vindos do contexto de segurança, nunca da requisição), **data/hora**, **operação**, **entidade + id**, **campos alterados com valor anterior e novo** e **justificativa** quando exigida. Só os campos que realmente mudaram são gravados.
- Auditado: criação/alteração de pedidos, mudança de status, desconto, cancelamento, criação/alteração/exclusão de clientes (endereço, telefone, nome…).
- Consulta: `GET /api/auditoria` e tela **Auditoria** (mais recentes primeiro) - **somente ADMIN e GERENTE**. Não existe rota para criar/alterar/excluir registros.
- Proteção de dados: **senhas e tokens nunca** entram na auditoria nem nos logs; na consulta, **telefones aparecem mascarados** (só os 4 últimos dígitos); os logs trazem apenas metadados (operação, entidade, id, usuário).

## Outras mudanças
- `application.properties`: URI do Mongo configurável por variável de ambiente; removido o usuário/senha padrão do Spring Security.
- Telas: Pedidos (lista com ações do gerente, novo pedido), Auditoria, sidebar por perfil, formulários de usuário com os 3 perfis, botão Sair; listagem de clientes passou a escapar HTML (XSS) e enviar CSRF.
- Produtos de exemplo são criados na primeira execução, para permitir registrar pedidos.

## Testes automatizados (`./mvnw test`)
- `StatusPedidoTest`, `PedidoTest`, `PedidoServiceTest`: fluxo, transições inválidas, saída, desconto (limite de 20%), cancelamento, bloqueio pós-cancelamento, total calculado no servidor.
- `AuditoriaServiceTest`, `ClienteServiceTest`: diff de campos, usuário/perfil/data, mascaramento, imutabilidade do repositório, auditoria de endereço/telefone.
- `UsuarioServiceTest`: perfis válidos, autoelevação, último ADMIN, senha criptografada.
- `AutorizacaoApiTest`, `AutorizacaoTelasTest` (MockMvc + Spring Security real): 401/403 por perfil e por rota, CSRF, desconto/cancelamento negados até ao ADMIN, API de usuários fechada, senha não exposta.

## Roteiro da demonstração (vídeo)
1. Login com `atendente`, `gerente` e `admin` (a sidebar muda por perfil).
2. **Bloqueios no backend** - logado como `atendente` (e depois como `admin`), abrir o console do navegador (F12) e chamar a API diretamente:
   `fetch('/api/pedidos/<id>/desconto', {method:'POST', headers: csrfHeaders(), body: JSON.stringify({percentual:10})}).then(r => console.log(r.status))` → **403**; como `gerente` → **200**.
3. Atendente: cadastrar/alterar cliente (mudar logradouro e telefone) e registrar pedido.
4. Gerente: avançar RECEBIDO → … → SAIU/RETIRADO → FINALIZADO; tentar pular etapa (erro); aplicar desconto de 10% e tentar 25% (erro); cancelar outro pedido com justificativa e tentar alterá-lo depois (erro).
5. Auditoria (gerente/admin): mostrar os registros gerados em cada passo acima.

## Estrutura
`config` (segurança, dados iniciais) · `controller` (APIs REST e telas) · `service` (regras de negócio, auditoria) · `repository` · `entity` · `dto` · `exception` · `security` · `util`.

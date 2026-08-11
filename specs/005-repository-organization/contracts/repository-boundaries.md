# Contract: Repository boundaries

## Ownership contract

| Path | Dono | Entrada/saída autorizada |
|---|---|---|
| `apps/urbana-connect-api/` | Urbana Connect backend | HTTP da POC, Mongo e Hermes Sessions API |
| `apps/poc-chat/` | ferramenta manual local | HTTP público da API Urbana; nenhum acesso a Mongo/Hermes |
| `integrations/hermes-agent/` | integração local | profile/plugin/scripts e runtime externo |
| `infra/local-poc/` | operação local | compõe processos, redes, proxies e volumes |
| `quality/` | qualidade | observa e chama interfaces documentadas |

## Runtime contract

```text
browser -> POST/GET /api/poc/conversations/{alias} -> Urbana Connect
Urbana Connect -> Hermes Sessions API
Urbana Connect -> MongoDB (transcript/projeção)
browser <- projection from Urbana Connect
```

O browser não conhece endpoint, token, sessão SQLite ou modelo do Hermes. O
Hermes não recebe dependência de implementação React. A reorganização só muda
paths de build/execução, não os contratos acima.

## Local infrastructure contract

`infra/local-poc/compose.yml` (ou o nome equivalente escolhido na migração)
deve conservar, salvo mudança explícita:

- nomes dos serviços;
- portas expostas;
- redes de dados, aplicação, proxy e egress;
- volumes persistentes;
- variáveis carregadas de `.env.poc`;
- healthchecks e dependências entre serviços.

Paths de `build.context`, mounts de arquivos locais e scripts podem mudar para
refletir a nova árvore, mas o resultado dentro dos containers deve continuar
equivalente.

## Compatibility contract

Durante a transição, scripts podem aceitar aliases de caminho antigo apenas se:

1. o caminho novo for o padrão documentado;
2. o alias não criar duas fontes canônicas;
3. houver teste que cubra ambos ou uma mensagem de migração clara;
4. o alias puder ser removido em tarefa posterior sem mudar runtime.

Nenhum alias deve copiar `.env.poc` ou expor segredo.

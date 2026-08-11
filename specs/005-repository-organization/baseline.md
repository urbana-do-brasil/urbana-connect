# Baseline: início da reorganização PEE-101

**Data**: 2026-08-11
**Branch observada**: `feat/pee-101`
**Objetivo**: registrar o estado antes das movimentações sem copiar, imprimir
ou alterar secrets.

## Evidência do worktree

- Branch atual: `feat/pee-101`.
- Alterações preservadas no início: 61 arquivos modificados/staged e 34
  arquivos não rastreados, totalizando 95 entradas no status resumido.
- Não foi executado `git reset`, `git checkout` destrutivo, `git clean` ou
  remoção de arquivo.
- `.env.poc`: ignorado pelo Git, modo local `600`; seu conteúdo não foi lido ou
  registrado.

## Fontes atuais identificadas

| Fonte | Papel atual | Ação desta spec |
|---|---|---|
| `app/` | backend Java/Spring e setup legado local | mover para `apps/urbana-connect-api/`; classificar Compose/dev-env concorrentes |
| `poc-chat/` | React/Vite/Nginx e testes do chat | mover para `apps/poc-chat/` |
| `hermes/profile/`, `hermes/plugins/`, `hermes/scripts/` | integração/configuração local Hermes | mover para `integrations/hermes-agent/` |
| `hermes/docker-compose.poc.yml` e proxies | stack local completo | mover para `infra/local-poc/` |
| `corpus/` | corpus e runner Ruby | mover para `quality/conversation-corpus/` |
| `infra/k8s/` | Kustomize de deploy | mover para `infra/kubernetes/` se todos os consumidores forem atualizados |

## Riscos já existentes

- A duplicata `app/src/main/java/.../PocReceptionWorker 2.java` declara a
  mesma classe pública e já impedia o build normal; ela permanece preservada.
- Há cópias divergentes ou geradas com sufixo ` 2` em `app/`, `hermes/`,
  `poc-chat/`, `corpus/` e specs. Elas serão classificadas, não apagadas
  automaticamente.
- Foram encontradas 36 áreas/arquivos com referências aos paths históricos em
  scripts, Compose, CI, docs e specs; a lista completa será corrigida ou
  classificada no inventário da implementação.
- `app/docker-compose.yml` e `hermes/docker-compose.poc.yml` são entrypoints
  diferentes; apenas o Compose completo da POC será o entrypoint canônico novo.

## Baseline do teste estrutural

O teste `quality/system-e2e/repository-structure.contract.sh` foi criado antes
da migração e executado. Ele falhou como esperado com 11 achados: os diretórios
alvo ainda não existiam e os diretórios históricos ainda estavam na raiz. Isso
é a evidência test-first da condição que a refatoração precisa resolver, não um
sucesso simulado.

## Topologia a preservar

Os nomes de serviço, portas, redes, volumes, healthchecks, contratos HTTP e
variáveis carregadas de `.env.poc` serão comparados antes/depois sem registrar
valores interpolados. Nenhuma coleção Mongo, sessão Hermes ou volume Docker
será removido como parte da reorganização.

### Snapshot sanitizado do Compose atual

`docker compose ... config --services` retornou os serviços:

```text
hermes-profile-init
openrouter-proxy
mongodb
urbana-connect
hermes
hermes-ingress-proxy
poc-chat
```

As chaves declaradas no YAML também foram registradas sem interpolar o
ambiente: redes `data`, `hermes_app`, `proxy_control`, `llm_egress`,
`poc_ingress`; volumes `mongodb_data` e `hermes_data`. A versão local do
Compose não suporta `config --networks`, então a verificação de redes foi feita
por leitura estrutural do manifesto, sem imprimir qualquer valor de secret.

# Data Model: Organização do monorepo Urbana Connect

Esta feature não cria coleção, entidade de negócio ou migração de dados. O
modelo abaixo descreve ownership e fronteiras físicas para que a reorganização
seja verificável.

## Unidades de ownership

| Unidade | Local alvo | Responsabilidade | Não deve conter |
|---|---|---|---|
| `UrbanaConnectApi` | `apps/urbana-connect-api/` | API Spring, domínio, persistência Mongo, adapter Hermes e testes Java | código React, Compose completo ou runtime upstream |
| `PocChat` | `apps/poc-chat/` | UI React, cliente HTTP da POC, Nginx e testes rápidos | acesso direto ao Mongo/Hermes ou secrets |
| `HermesIntegration` | `integrations/hermes-agent/` | profile, plugin, pin e scripts mantidos localmente | fonte do Hermes upstream ou dados de sessão |
| `LocalPocRuntime` | `infra/local-poc/` | Compose, proxies, redes, volumes nomeados e operação local | regras de negócio ou código de aplicação |
| `KubernetesInfrastructure` | `infra/kubernetes/` | manifests e overlays existentes | código fonte das aplicações |
| `ConversationQuality` | `quality/conversation-corpus/` | corpus, fixtures, schema, runner e relatórios ignorados | secrets e dados de produção |
| `SystemE2e` | `quality/system-e2e/` | jornadas que exercitam vários processos | lógica reutilizável de produção |

## Grafo de dependência permitido

```text
apps/poc-chat
       │ HTTP /api/poc
       ▼
apps/urbana-connect-api ───────► MongoDB
       │ Hermes Sessions API
       ▼
runtime Hermes (externo)

infra/local-poc ── compõe os processos e proxies
quality/* ──────── observa/valida os processos, sem virar dependência de runtime
```

## Invariantes de migração

- nenhuma coleção Mongo é renomeada;
- nenhum volume Docker é removido ou recriado deliberadamente;
- nenhum alias de conversa, contrato HTTP ou campo de transcript muda;
- `localStorage` do chat continua contendo somente metadados visuais versionados;
- a mensagem outbound continua uma string textual literal do Hermes;
- `.env.poc` continua local, ignorado e fora do modelo versionado;
- paths são metadados de build/operação, não dados persistidos.

## Artefatos gerados

`node_modules`, `dist`, `coverage`, `playwright-report`, `test-results`,
`.gradle`, `build`, resultados do corpus e logs locais não são unidades de
ownership nem fonte canônica. Permanecem ignorados e podem ser recriados pelos
comandos documentados.

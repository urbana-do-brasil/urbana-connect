# Data Model: conjunto versionável da etapa 1

Esta feature não cria entidade de negócio nem altera documentos Mongo. O modelo
abaixo descreve os artefatos que precisam ser classificados para o commit.

## ReleaseArtifact

| Campo | Tipo | Regra |
|---|---|---|
| `path` | caminho relativo | deve estar dentro do repositório e não conter secret real |
| `category` | enum | `canonical`, `generated`, `secret`, `local-tool`, `duplicate`, `legacy` |
| `sourceOfTruth` | booleano | somente um arquivo canônico por responsabilidade |
| `trackedIntent` | enum | `include`, `ignore`, `quarantine`, `legacy-document` |
| `reason` | texto | decisão auditável, especialmente para duplicatas |

## CanonicalBoundary

| Área | Fonte canônica | Fora do conjunto |
|---|---|---|
| Backend | `apps/urbana-connect-api/` | `app/`, build, duplicate sources |
| Chat | `apps/poc-chat/` | `poc-chat/`, coverage, node_modules |
| Hermes local | `integrations/hermes-agent/` | upstream Hermes, `hermes/` antigo |
| Runtime local | `infra/local-poc/` | volumes, `.env.poc`, proxies duplicados |
| Qualidade | `quality/` | results, reports gerados, cópias ` 2` |
| Ferramenta local | nenhuma fonte Git | `.codex/`, IDE, caches |

## OperationalSignal

| Signal | Obrigatório | Critério |
|---|---:|---|
| API liveness | sim | processo HTTP responde |
| API readiness | sim | aplicação aceita tráfego e Mongo responde ao ping |
| Mongo health | sim | serviço Mongo saudável |
| SMTP health | não na POC | não pode derrubar readiness local |
| Hermes contract | sim para E2E | Sessions API e proxy respondem |

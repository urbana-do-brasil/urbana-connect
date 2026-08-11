# Research: fechamento da POC Hermes

## R1 — Estado funcional da POC

**Decision**: manter o pipeline local Hermes-first como contrato desta feature.

**Evidence**: a prova anterior comparou a resposta outbound por comprimento e
SHA-256 na história Hermes, no Mongo e na projeção HTTP, além do cenário live do
chat com múltiplas conversas.

**Rationale**: o fechamento deve reduzir risco de release sem reintroduzir
tratamento conversacional local.

## R2 — Fronteira webhook versus POC

**Decision**: não migrar o webhook nesta feature.

**Evidence**: `WebhookController` injeta `ConversationFlowService`, enquanto o
perfil POC usa `/api/poc/conversations` e `ReceptionOrchestrator` com Hermes.
`AiConfiguration` ainda registra `GeminiAiGateway`.

**Rationale**: são contratos, testes, saída WhatsApp e decisão operacional
distintos. Misturá-los impediria uma limpeza reversível e ampliaria o risco.

## R3 — Worktree e staging

**Decision**: staging seletivo e quarentena local ignorada.

**Evidence**: a branch está alinhada ao remoto, mas o worktree possui remoções
unstaged, renomes parciais staged, centenas de destinos untracked, `.codex` e
arquivos com sufixo ` 2`.

**Rationale**: `git add -A` poderia versionar ferramenta local ou produzir um
commit parcial que apagasse a aplicação.

## R4 — Actions do frontend

**Decision**: corrigir os dois identificadores inválidos antes do commit.

**Evidence**: `actions/checkout` frontend usa um SHA inexistente; `setup-node`
usa uma string de 39 caracteres. A ref `v4` de setup-node resolve para um SHA
de 40 caracteres verificável remotamente.

## R5 — Readiness local

**Decision**: Mongo e estado de aceitação são obrigatórios; SMTP é opcional na
POC.

**Evidence**: `/api/v1/readiness` retorna `READY`, mas `/actuator/health`
retorna `503` porque `MailHealthIndicator` tenta autenticar em SMTP ausente.

**Rationale**: o sinal usado para iniciar o chat deve representar dependências
da POC, sem fingir que e-mail local está configurado.

## R6 — Artefatos não versionáveis

**Decision**: não versionar `.codex`, quarentena, resultados, caches, IDE files,
`.env.poc` ou duplicatas ` 2`; manter apenas templates e código canônico.

**Evidence**: `.env.poc` está ignorado com modo 600; resultados, `node_modules`,
build, coverage e `.gradle` já estão ignorados; `.codex` e duplicatas ainda
aparecem como untracked.

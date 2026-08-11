# Feature Specification: Fechamento da POC Hermes e higiene de release

**Feature Branch**: `feat/pee-101`
**Created**: 2026-08-11
**Status**: Verified — POC local, higiene e critérios de release validados em `feat/pee-101`
**Ticket Jira**: PEE-101
**Input**: concluir a primeira etapa local, deixar o conjunto de alterações
versionável e corrigir os riscos encontrados na revisão da POC Hermes.

## 1. Contexto

A POC local já comprova o fluxo textual `poc-chat → Urbana Connect → MongoDB
→ Hermes → MongoDB → poc-chat`, com a resposta do Hermes preservada no caminho
normal. Entretanto, a branch ainda contém movimentações parcialmente staged,
arquivos duplicados com sufixo ` 2`, artefatos de trabalho do Codex, referências
ambíguas e correções de CI/healthcheck que impedem tratar o resultado como um
release limpo.

Esta feature fecha a etapa local e prepara um commit auditável em
`feat/pee-101`, preservando alterações existentes e sem apagar conteúdo sem
quarentena reversível. Ela não muda o comportamento conversacional Hermes-first
já validado.

### Decisões de escopo

- O objetivo desta entrega é a POC local e sua versionabilidade, não a migração
  do webhook real do WhatsApp.
- O webhook de produção continua explicitamente legado, baseado no fluxo
  `ConversationFlowService`/Gemini, até uma feature própria de migração.
- `.env.poc` permanece local, ignorado e nunca será movido, impresso ou
  commitado.
- Cópias ambíguas serão comparadas e movidas para quarentena local ignorada,
  preservando seu conteúdo; não haverá descarte irreversível.
- O commit só será criado depois de QA independente, diff staged revisado e
  ausência de pendências desta spec.

## 2. User stories

### US1 — Ter um conjunto de arquivos seguro para versionar (P1)

Como mantenedor do repositório, quero separar código canônico de artefatos
locais e duplicatas, para que o commit da POC seja reproduzível e não carregue
segredos, caches ou cópias divergentes.

Critérios observáveis:

1. O conjunto versionável contém as duas aplicações, integração Hermes, infra,
   qualidade, contratos e documentação canônicos.
2. `.env.poc`, `.codex`, caches, resultados, IDE files e duplicatas ` 2` não
   aparecem no conjunto staged.
3. A duplicata Java que quebra o build normal deixa de existir no source set
   canônico; seu conteúdo permanece recuperável na quarentena local.
4. O diff staged representa movimentações e alterações completas, sem apagar
   acidentalmente uma aplicação por ter deixado o destino apenas como arquivo
   untracked.

### US2 — Ter CI e runtime local com sinais operacionais confiáveis (P1)

Como pessoa desenvolvedora, quero que CI e healthchecks reflitam a topologia
real, para não receber uma falsa indicação de falha nem um falso sucesso por
causa de ordem de inicialização.

Critérios observáveis:

1. Todas as Actions referenciadas no workflow frontend usam SHAs válidos e o
   job consegue iniciar.
2. A API possui um healthcheck de prontidão que depende do Mongo e o chat só é
   considerado pronto depois da API estar pronta.
3. O healthcheck da POC não fica `DOWN` somente porque SMTP opcional não foi
   configurado; a disponibilidade da POC é medida por seus recursos
   obrigatórios.
4. A topologia Compose, portas, redes, volumes e credenciais permanecem
   equivalentes, sem imprimir valores sensíveis.

### US3 — Preservar o comportamento Hermes-first validado (P1)

Como pessoa testadora, quero que o fechamento não reintroduza tratamento local,
para continuar avaliando a conversa real da Urba no Hermes.

Critérios observáveis:

1. Mensagem textual inbound é persistida antes do dispatch remoto.
2. A resposta textual do Hermes permanece igual na persistência, projeção HTTP
   e chat local.
3. Uma falha/timeout não cria uma fala artificial da Urba.
4. Múltiplas conversas continuam isoladas e o polling segue funcionando.

### US4 — Ter rastreabilidade correta da etapa (P2)

Como pessoa revisora, quero que specs, quickstarts e CI descrevam o estado real,
para distinguir claramente a POC local da futura integração WhatsApp.

Critérios observáveis:

1. As specs 001–006 informam status, branch, evidências e fronteiras sem
   afirmar que o webhook WhatsApp já usa Hermes.
2. O quickstart aponta somente para caminhos canônicos e comandos executáveis.
3. A documentação registra os itens intencionalmente fora de escopo e o
   próximo passo obrigatório para WhatsApp/Hermes.

## 3. Requisitos funcionais

- **FR-001**: O commit da feature MUST conter somente arquivos canônicos,
  documentação relacionada e configuração necessária para a POC; não deve
  conter `.env.poc`, `.codex`, caches, resultados, IDE files ou duplicatas.
- **FR-002**: Toda cópia ` 2` MUST ser comparada ao arquivo canônico e,
  quando não fizer parte do produto, MUST ser preservada fora do conjunto
  versionável em quarentena local ignorada.
- **FR-003**: A fonte Java duplicada `PocReceptionWorker 2.java` MUST deixar de
  participar do source set usado pelo Gradle sem descartar seu conteúdo.
- **FR-004**: O índice Git final MUST representar a migração de `app/` para
  `apps/urbana-connect-api/`, `poc-chat/` para `apps/poc-chat/`, `hermes/` para
  `integrations/` e `infra/local-poc/`, sem deleções acidentais.
- **FR-005**: Workflows MUST referenciar commits de Actions existentes e
  executar os jobs backend e frontend nos caminhos novos.
- **FR-006**: A API POC MUST expor um sinal de prontidão que só seja positivo
  quando suas dependências obrigatórias estiverem disponíveis.
- **FR-007**: Dependências opcionais de desenvolvimento, especialmente SMTP,
  MUST NOT tornar o healthcheck obrigatório da POC indisponível.
- **FR-008**: Compose MUST declarar a ordem de prontidão da API e do chat por
  healthcheck, mantendo a topologia funcional atual.
- **FR-009**: O fechamento MUST preservar o pass-through textual e os controles
  de idempotência, lease, polling, sessão e reconciliação já validados.
- **FR-010**: A documentação MUST distinguir a POC `/api/poc/conversations`
  do webhook `/api/webhook`, que continua fora desta feature.
- **FR-011**: A branch MUST receber um commit somente após todos os critérios
  desta spec, testes relevantes e QA independente passarem.

## 4. Requisitos não funcionais

- **NFR-001 — Segurança**: nenhum secret real deve aparecer no índice, diff,
  log de validação ou artefato da spec.
- **NFR-002 — Reversibilidade**: quarentena local deve preservar conteúdo e
  permitir restauração sem consultar o Git remoto.
- **NFR-003 — Compatibilidade**: a correção não deve alterar contratos HTTP,
  nomes de serviço, portas, redes, volumes, modelos ou prompts do Hermes.
- **NFR-004 — Qualidade**: a suíte Java, frontend, scripts Hermes, corpus,
  contratos e E2E relevante devem permanecer verdes.
- **NFR-005 — Rastreabilidade**: o commit deve ser revisável por arquivo e
  vinculado à PEE-101, sem misturar planos pessoais ou arquivos de ferramenta.

## 5. Critérios de aceite

1. O contrato de fronteira de release passa e não encontra secret real,
   duplicata, `.codex` ou artefato gerado no conjunto versionável.
2. `git diff --check` passa e `git diff --cached --check` passa depois da
   preparação do commit.
3. O workflow frontend não contém SHA inexistente ou SHA com tamanho inválido.
4. Compose valida e a API/chamada de prontidão funcionam com Mongo disponível,
   sem exigir SMTP para a POC.
5. Java, frontend, plugin/profile Hermes, corpus, Playwright e prova literal
   Hermes → Mongo → API → UI passam novamente após os ajustes.
6. As referências e status das specs não afirmam que o webhook WhatsApp já foi
   migrado para Hermes.
7. QA independente aprova a árvore, o índice e os riscos residuais.
8. Um commit é criado em `feat/pee-101` somente quando não houver tarefa
   pendente desta spec.

## 6. Edge cases

- Um arquivo ` 2` pode ser divergente e conter trabalho útil; ele não pode ser
  sobrescrito silenciosamente.
- O Docker pode estar parado durante validação; checks estáticos continuam
  possíveis, mas o E2E live deve ser classificado, nunca simulado.
- Hermes pode iniciar depois da API; a aplicação deve manter a recuperação de
  turnos sem tratar a corrida inicial como perda de mensagem.
- O health endpoint Actuator pode agregar verificadores opcionais; seu sinal
  não deve contradizer a prontidão funcional da POC.
- O webhook WhatsApp pode continuar compilando no fluxo legado; isso não é
  evidência de que ele passou a usar Hermes.

## 7. Observabilidade e validação

- Contrato shell de fronteira do release, busca de paths e diff staged.
- Validação dos SHAs de Actions e parse do workflow.
- `docker compose config`, healthchecks e readiness HTTP sanitizados.
- Gradle/JUnit/JaCoCo, Vitest, typecheck, lint e build Docker do chat.
- Smokes Hermes, isolamento, superfície de ferramentas, plugin e profile.
- Corpus Ruby, Playwright determinístico/live e prova literal de round-trip.
- QA independente antes do commit.

## 8. Fora de escopo

- Migrar o webhook real do WhatsApp para Hermes.
- Alterar `ConversationFlowService`, Gemini, gateway WhatsApp ou manifests de
  produção para introduzir o novo fluxo.
- Deploy, homologação, produção, alteração de credenciais ou dados persistidos.
- Anexos, streaming, SSE/WebSocket ou mudança de modelo/provedor.
- Apagar definitivamente arquivos ambíguos sem cópia recuperável.

## 9. Decisões em aberto

Não há decisão de produto pendente para esta feature. A migração WhatsApp →
Hermes será registrada como próxima feature independente após este fechamento.

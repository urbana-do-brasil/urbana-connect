# Research: Resiliência de turnos Hermes

## Decision 1 — Usar MongoDB como fila durável da POC

**Decision**: Persistir o evento aceito e o trabalho antes do `202`; manter o
`MessageBatcher` em memória somente como otimização da janela; recuperar pendências
do Mongo após restart.

**Rationale**: Hoje a entrada textual pode existir apenas no batcher até o flush.
O Mongo já é a fonte canônica e está disponível no compose local, portanto atende
durabilidade e não introduz Redis, Kafka ou outro serviço.

**Alternatives considered**:

- Manter o batcher somente em memória: rejeitado porque reload/restart pode perder
  mensagem aceita.
- Criar broker externo: rejeitado por escopo e custo operacional da POC.

## Decision 2 — Worker assíncrono com serialização por contato

**Decision**: O POST encerra após persistência/claim de fila; um executor local
processa contatos distintos em paralelo e impede dois turnos do mesmo contato.

**Rationale**: O chat já trabalha com `202` + polling. O processamento síncrono no
scheduler cria bloqueio entre contatos e acopla a resposta do Hermes ao ciclo do
HTTP. A exclusão persistente continua necessária para proteger contra restart ou
mais de uma instância.

**Alternatives considered**:

- Apenas `CompletableFuture` sem claim Mongo: rejeitado porque duas instâncias
  ainda poderiam reivindicar o mesmo contato.
- Aumentar somente o timeout HTTP: rejeitado como mitigação insuficiente para
  perda de conexão, restart e resposta ambígua.

## Decision 3 — Falha após dispatch é ambígua por padrão

**Decision**: Read timeout, reset, `502/504`, `5xx` sem garantia de rejeição e
corpo inválido após o envio não podem virar retry automático. O turno vai para
`RECONCILING` e mantém o gate.

**Rationale**: O cliente não consegue provar se Hermes recebeu e continua
executando a solicitação. Retentar nesse ponto foi a causa direta dos turnos
concorrentes observados.

**Alternatives considered**:

- Tratar todo `status=0`, `429` e `5xx` como retryable: rejeitado porque mistura
  falha antes do dispatch com resultado desconhecido.
- Liberar lease no `finally`: rejeitado durante estado ambíguo.

## Decision 4 — Conciliar pelo histórico Hermes antes de liberar ou repetir

**Decision**: Registrar um checkpoint da sessão antes da chamada e, em estado
`RECONCILING`, observar o histórico. Uma nova mensagem de assistente depois do
checkpoint finaliza o turno original; sem evidência, o gate continua fechado.

**Rationale**: O port Hermes já expõe `history(sessionId)`. A regra de um único
turno por sessão torna um novo item de assistente após o checkpoint observável para
o turno corrente. O contrato precisa ser testado com Hermes real antes de aceitar
essa evidência como definitiva.

**Alternatives considered**:

- Reenviar a mesma mensagem: rejeitado por duplicidade potencial.
- Fabricar resposta de fallback: rejeitado pela spec e pela segurança
  conversacional.

## Decision 5 — Polling sem deadline terminal no navegador

**Decision**: O tracker continua consultando enquanto a projeção informar trabalho
não terminal; pode fazer backoff e informar “demorando”. Erro temporário de GET
não muda o turno para retryable.

**Rationale**: O frontend não é dono do processamento e o antigo limite de 120s
produzia falso erro. A projeção do backend será a autoridade para `retryAllowed`.

**Alternatives considered**:

- WebSocket/SSE: rejeitado porque não é necessário para a POC e amplia o escopo.
- Aumentar o limite fixo para outro valor: rejeitado porque apenas desloca o falso
  erro.

## Decision 6 — Parâmetros iniciais configuráveis

**Decision**: Usar timeout Hermes inicial de 180s, lease e claim de 240s,
backoff de polling e heartbeat configuráveis; calibrar com latência observada.

**Rationale**: Uma resposta real anterior levou aproximadamente 43s e o timeout
atual é 30s. O número é mitigação operacional, nunca a regra de segurança para
retry.

**Risk**: Se Hermes não oferecer limite/cancelamento observável, uma execução
externa excepcionalmente longa pode exigir intervenção manual. O sistema deve
permanecer fail-closed e registrar o estado. O timeout e os prazos de lease/claim
devem ser alterados juntos quando a latência observada exigir ajuste; o claim não
pode expirar antes da chamada Hermes terminar ou entrar em reconciliação.

## Estado da validação operacional

A revalidação final ocorreu com Docker Desktop 28.0.1 no contexto
`desktop-linux`. A suíte completa `./gradlew --no-daemon --max-workers=1 test
jacocoTestReport` passou com 327 testes, zero falhas e JaCoCo em 82.94% de linhas;
`bootJar` também passou. Os serviços locais ficaram saudáveis, com timeout Hermes
de 180s e lease/claim de 240s efetivos no container da Urbana.

Os smokes contratual, live Hermes → OpenRouter e isolamento passaram. Um E2E real
via `poc-chat` produziu `202/QUEUED` → `COMPLETED`, uma chamada Hermes e uma saída
canônica; a consulta Mongo confirmou o turno, a pendência e a lease sem
duplicidade. O Playwright live também passou com três contatos, alternância e
reload, e os três transcripts recentes ficaram isolados e concluídos.

A restrição anterior do socket Docker e o erro de lock do Gradle foram resolvidos;
eles permanecem registrados no histórico de `tasks.md`. A correção complementar
do polling sequencial foi validada com regressão unitária, Playwright US6 e QA
independente. Um smoke live posterior excedeu 180s e entrou em
`HERMES_TIMEOUT_AFTER_DISPATCH`, confirmando o comportamento fail-closed de
`RECONCILING`; esse caso permanece classificado como latência/indisponibilidade
externa, não como defeito do frontend.

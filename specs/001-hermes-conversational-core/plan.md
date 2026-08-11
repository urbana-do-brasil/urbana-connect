# Implementation Plan: Núcleo conversacional Hermes-first

**Branch**: `001-hermes-conversational-core` | **Date**: 2026-08-04 | **Spec**: [spec.md](./spec.md)
**Consolidated validation branch**: `feat/pee-101`
**Input**: Feature specification from `/specs/001-hermes-conversational-core/spec.md`

## Summary

Construir uma trilha POC, inicialmente isolada do webhook produtivo, na qual a Urbana Connect recebe eventos sintéticos equivalentes aos do WhatsApp, persiste o transcript e os estados de negócio, resolve uma sessão persistente por contato e delega a condução conversacional a um perfil dedicado do Hermes Agent. O Hermes usa sua Sessions API nativa, o modelo `openai/gpt-5.6-luna` via OpenRouter e somente um plugin de ferramentas de domínio da Urbana. A aplicação valida todas as mutações comerciais e continua sendo a autoridade sobre o canal, identidade, pagamento e handoff.

## Technical Context

**Language/Version**: Java 21 LTS na Urbana Connect; Python fornecido pelo runtime oficial do Hermes apenas para o plugin de extensão  
**Primary Dependencies**: Spring Boot 3.4.13, Gradle 8.x, Hermes Agent `v2026.8.3` Sessions API, OpenRouter  
**Storage**: MongoDB para transcript, fatos, mapeamento de sessões e execuções do corpus; SQLite interno do Hermes para sessões  
**Testing**: JUnit 5, Mockito, Spring Boot Test, Testcontainers, testes de contrato HTTP e runner sintético  
**Target Platform**: serviço backend em container, Hermes como processo/sidecar dedicado, execução local e posterior k3s  
**Project Type**: backend web-service com runtime conversacional adjacente  
**Performance Goals**: medir duração por turno, tokens e custo; nenhuma meta eliminatória na primeira POC  
**Constraints**: Clean Architecture; JaCoCo mínimo de 60%; segredos fora do repositório; nenhum acesso do Hermes a WhatsApp, Mongo bruto, terminal, arquivos ou navegador; processamento serial por contato  
**Scale/Scope**: cinco personas, no mínimo três execuções por persona, contatos sintéticos e links não transacionais  
**Local Provider**: `openrouter/openai/gpt-5.6-luna`, `reasoning_effort=max` na primeira bateria  
**Environmental blockers observed**: máquina atualmente expõe JDK 17, não JDK 21; Docker Desktop está parado; Hermes CLI não está no `PATH`; a chave OpenRouter não está configurada  

## Constitution Check

*GATE: passed before research and rechecked after design.*

| Principle | Status | Evidence |
| --- | --- | --- |
| Stack oficial | PASS | A aplicação permanece Java 21/Spring Boot 3.4.x/Gradle/MongoDB; Hermes é uma dependência externa escolhida explicitamente, não substitui o stack da aplicação. |
| Clean Architecture | PASS | Sessões, mídia e ferramentas entram por portas; clientes HTTP, Mongo, simulador e canal permanecem em adapters. |
| Specification-first e test-first | PASS | `spec.md` e contratos antecedem tarefas; cada história inclui testes antes da implementação. |
| Quality gate | PASS | Plano mantém `check`, JaCoCo 60%, testes focados e QA independente. |
| Homolog-first | PASS | Gate definido como local, homologação sintética e somente depois produção controlada. |

O plugin Python é uma extensão do runtime Hermes e fica separado do domínio Java. Não há violação de stack porque nenhuma regra de negócio é implementada nele: handlers apenas encaminham chamadas tipadas para a Urbana Connect e retornam JSON.

## Architecture

```text
Synthetic runner / future WhatsApp webhook
                  |
                  v
       Urbana Connect ingress adapter
                  |
       persist + deduplicate + serialize
                  |
          ReceptionOrchestrator
          /                  \
         v                    v
 Mongo authorities      Hermes Sessions API
 transcript/facts       persistent session/contact
 business state                  |
         ^                       v
         |              restricted Urbana plugin
         +------ validated domain tool calls
                  |
                  v
       validated minimal reply contract
                  |
        persist + simulator output
        (future: WhatsApp send)
```

### Authority boundaries

- **Hermes**: sequência conversacional, continuidade da sessão, escolha da próxima fala, uso das ferramentas permitidas e memória de trabalho da conversa.
- **Urbana Connect**: contato interno, transcript canônico, fatos auditáveis, catálogo, termos, pagamento, handoff, idempotência, saída para o canal e recuperação de desastre.
- **MongoDB**: autoridade de auditoria e transação; não é reenviado integralmente em turnos normais.
- **WhatsApp**: continua acessível exclusivamente pela Urbana Connect e não participa da primeira POC local.

### Incremental routing

O fluxo Hermes será ativado somente no endpoint do simulador durante a POC. O `WebhookController` e o fluxo atual permanecem inalterados até que o corpus local e a homologação sintética passem. A futura troca do roteamento do webhook será uma tarefa explícita posterior, protegida por feature flag.

### Hermes profile

- Perfil isolado `urba-receptionist`, com `HERMES_HOME` próprio.
- `SOUL.md` define identidade transparente, tom e limites da Urba.
- OpenRouter usa o modelo `openai/gpt-5.6-luna` e esforço inicial `max`.
- Apenas o toolset `urbana-domain` é habilitado para o API server; o processo falha no startup se a superfície observada divergir.
- Memória global entre clientes, terminal, filesystem, browser, web, skills autogeradas, delegação e mensageria permanecem desabilitados.
- Sessões e seu histórico permanecem no SQLite interno do perfil; fatos duráveis do cliente permanecem na Urbana Connect.

### Domain tool bridge

Para a POC, um plugin de projeto oficial do Hermes registra somente as ferramentas acordadas e encaminha cada chamada a endpoints internos autenticados da Urbana Connect. A ferramenta não recebe `contactId`, turno ou chave idempotente do modelo. Na versão pinada, a Sessions API entrega ao plugin `task_id=session_id`; antes de chamar o Hermes, a Urbana Connect cria um lease atômico e expirável `sessionId -> turnId/contactId`. O plugin envia apenas o ID de sessão recebido do runtime e sua identidade técnica. O backend resolve o lease ativo, deriva contato, turno, mensagem de origem e idempotência, e rejeita lease ausente, expirado ou fora de `RUNNING`.

A saída de `/api/sessions/{id}/chat` é texto livre; a API nativa não garante `response_format`. O JSON mínimo é, portanto, um contrato desejado do agente e permanece não confiável. Antes de publicar, a Urbana Connect faz parse estrito e reconcilia `nextAction` com o estado comercial e o ledger real de ferramentas daquele turno. Texto inválido ou ação não comprovada nunca avança o fluxo.

O MCP foi considerado, mas não será introduzido na primeira POC: a integração Spring AI MCP atual exigiria uma nova linha de dependências e compatibilidade adicional com o Spring Boot existente. O plugin é a extensão oficialmente recomendada pelo Hermes para ferramentas de projeto e mantém o primeiro experimento menor. A migração para MCP continua possível sem alterar os contratos de domínio.

### Session and recovery strategy

1. Resolver ou criar `contactId` sem enviar o número bruto ao Hermes.
2. Resolver `contactId -> hermesSessionId` no Mongo.
3. Criar uma sessão nativa quando o vínculo não existir.
4. Adquirir um lease de turno para a sessão e enviar o turno para `/api/sessions/{id}/chat`, com imagem inline quando aplicável.
5. Validar a resposta livre, reconciliar ferramentas/estado, persistir resposta, ações e métricas e então revogar o lease antes de publicar o resultado no canal simulado.
6. Se a sessão tiver desaparecido, criar outra e reconstruir somente nesse caso, usando fatos confirmados e um recorte auditável do transcript.
7. Se a resposta indicar rotação de sessão por compressão, substituir atomicamente o vínculo ativo, preservando a linhagem anterior.
8. Nunca recriar o contexto em cada mensagem normal.

### Reliability strategy

- Chave idempotente por evento de entrada e por operação comercial, sempre derivada no backend a partir de turno, ferramenta e argumentos normalizados.
- Lease de turno adquirido antes da chamada e revogado em `finally`; TTL limita chamadas tardias após timeout.
- Fila/lock serial por `contactId`; a POC usa coordenação local com interface substituível por mecanismo distribuído.
- Timeout configurável e uma recuperação segura; nenhum retry cego após ferramenta mutável sem consulta da chave idempotente.
- Saída livre é sempre não confiável; JSON inválido ou ação divergente do ledger resulta em fallback seguro ou handoff, sem avanço comercial.
- Mensagem recebida é persistida antes de chamar o Hermes.
- Handoff muda a conversa para modo humano antes de emitir qualquer confirmação ao contato.

## Project Structure

### Documentation (this feature)

```text
specs/001-hermes-conversational-core/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── agent-output.schema.json
│   ├── domain-tools.md
│   ├── hermes-sessions.md
│   └── simulator-api.yaml
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
apps/urbana-connect-api/
├── src/main/java/br/com/urbana/connect/
│   ├── domain/reception/
│   │   ├── model/
│   │   └── port/out/
│   ├── application/reception/
│   ├── infrastructure/hermes/
│   ├── infrastructure/persistence/mongodb/reception/
│   └── interfaces/rest/poc/
├── src/main/resources/
└── src/test/java/br/com/urbana/connect/

hermes/
├── profile/
│   ├── SOUL.md
│   └── config.yaml.example
├── plugins/urbana-domain/
│   ├── plugin.yaml
│   ├── __init__.py
│   ├── schemas.py
│   └── tools.py
└── scripts/
    ├── install-local.sh
    └── run-local.sh

quality/conversation-corpus/
├── scenarios/
├── fixtures/
└── README.md
```

**Structure Decision**: implementar um bounded context `reception` e um ingresso POC separados do fluxo rígido atual. Isso reduz regressão e permite comparar os dois caminhos antes de promover o Hermes ao webhook real. O runtime Hermes e seu plugin permanecem em `hermes/`, sem regras comerciais próprias.

## Phase 0 — Research outcome

As decisões e alternativas estão consolidadas em [research.md](./research.md). Não permanecem marcadores `NEEDS CLARIFICATION`.

## Phase 1 — Design outcome

- Entidades e transições: [data-model.md](./data-model.md)
- Contrato da Sessions API: [contracts/hermes-sessions.md](./contracts/hermes-sessions.md)
- Ferramentas permitidas: [contracts/domain-tools.md](./contracts/domain-tools.md)
- Saída mínima: [contracts/agent-output.schema.json](./contracts/agent-output.schema.json)
- Simulador local: [contracts/simulator-api.yaml](./contracts/simulator-api.yaml)
- Execução local: [quickstart.md](./quickstart.md)

## Post-design Constitution Check

PASS. O design mantém o runtime externo numa borda, não altera o webhook produtivo, não coloca regras comerciais no plugin e prevê testes/quality gate antes da promoção. Segredos aparecem apenas como nomes de variáveis e arquivos de exemplo sem valores.

## Complexity Tracking

| Decision | Why Needed | Simpler Alternative Rejected Because |
| --- | --- | --- |
| Novo bounded context POC ao lado do fluxo atual | Permite testar autonomia conversacional persistente sem regressão imediata no WhatsApp | Reescrever `ConversationFlowService` agora misturaria validação experimental com tráfego existente e ampliaria o risco. |
| Plugin Hermes restrito | O agente precisa consultar e solicitar mutações de domínio durante o próprio loop | Colocar catálogo inteiro no prompt duplica contexto, permite desatualização e não cria auditoria determinística. |
| Transcript duplicado em Hermes e Mongo | Hermes gerencia contexto; Urbana precisa de auditoria, UI e recuperação | Usar apenas uma das stores quebra respectivamente autonomia conversacional ou governança operacional. |

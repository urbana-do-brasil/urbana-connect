# Implementation Plan: Resiliência de turnos Hermes no chat manual da POC

**Branch**: `003-hermes-turn-resilience` | **Date**: 2026-08-07 | **Spec**: [spec.md](./spec.md)
**Consolidated validation branch**: `feat/pee-101`
**Input**: Feature specification from `/specs/003-hermes-turn-resilience/spec.md`

## Summary

A falha atual nasce da combinação de timeout síncrono do gateway Hermes,
retentativa imediata do ingress, revogação da lease enquanto o Hermes ainda pode
estar executando e limite fixo de acompanhamento no navegador. A correção será
um ciclo de vida durável para entradas e turnos da POC, apoiado no MongoDB já
existente, com worker local assíncrono e exclusão por conversa.

O aceite persistirá a entrada antes de qualquer chamada remota e retornará
`202/QUEUED`. Um executor local poderá processar contatos diferentes em paralelo,
mas reivindicará no máximo um turno ativo ou incerto por contato. Timeout de
transporte será classificado como ambíguo, manterá o turno em conciliação e nunca
disparará retry automático. A reconciliação consultará o histórico Hermes para
capturar uma resposta que tenha sido produzida depois da perda do transporte;
casos sem conclusão confiável permanecerão bloqueados para novo processamento.

O chat continuará usando polling da projeção canônica, com backoff e sem
transformar tempo decorrido em falha. Streaming, nova fila externa, mudança de
provedor e webhook real permanecem fora do escopo.

## Technical Context

**Language/Version**: Java 21 LTS no backend; TypeScript 5.x/React 19.2 no chat
**Primary Dependencies**: Spring Boot 3.4.13, Gradle 8.x, Spring Data MongoDB, RestClient, Vite 8
**Storage**: MongoDB como fonte durável de eventos, turnos, leases e tentativas; SQLite interno do Hermes permanece externo à Urbana
**Testing**: JUnit 5, Mockito, Spring Boot Test, Testcontainers quando aplicável; Vitest, React Testing Library e Playwright
**Target Platform**: containers locais da POC (Urbana Connect, Hermes, MongoDB e chat)
**Project Type**: backend web-service com frontend React local
**Performance Goals**: aceite da entrada em até 2s; polling inicial de 1s com backoff; três contatos devem poder aguardar processamento sem bloqueio global
**Constraints**: preservar Clean Architecture, quality gate JaCoCo 60%, secrets fora do Git, compatibilidade dos endpoints POC, nenhum deploy/produção e nenhum novo serviço externo
**Scale/Scope**: baixa escala local, com foco em uma pessoa testadora e poucos contatos simultâneos

## Constitution Check

*GATE: PASS antes da pesquisa e após o design.*

- **Stack oficial**: PASS. Java 21, Spring Boot, Gradle, MongoDB, TypeScript e
  React existentes permanecem a baseline.
- **Clean Architecture**: PASS. Estados e contratos ficam no domínio/aplicação;
  Mongo, HTTP Hermes e agendamento permanecem nas bordas.
- **Specification-First + Test-First**: PASS. A spec 003 precede o plano; cada
  transição nova terá teste falhando antes da implementação correspondente.
- **Qualidade automatizada**: PASS condicionado à execução final do backend,
  frontend, E2E e quality gate JaCoCo. Nenhuma tarefa será marcada concluída sem
  evidência.
- **Homolog primeiro**: PASS. Esta execução limita-se à branch local; eventual PR
  seguirá `feature -> hml` e não fará promoção automática.
- **Segurança e autoridade**: PASS. Não há alteração de credenciais, webhook,
  produção ou dependência externa além do JDK local usado para testar.

## Design

### Componentes e responsabilidades

1. **Durable POC ingress**: registra cada evento aceito no Mongo antes de retorná-
   lo ao cliente. O `MessageBatcher` continua como otimização da janela, mas não
   é mais a única cópia do evento; um ciclo de recuperação busca itens pendentes
   depois de reinício.
2. **POC worker/executor**: entrega lotes reivindicados ao orquestrador em um
   executor local com serialização por contato e paralelismo entre contatos. A
   reivindicação é condicional e idempotente.
3. **Turn lifecycle**: amplia o modelo de turno para distinguir `QUEUED`,
   `RUNNING`, `DELAYED`, `RECONCILING`, `COMPLETED`, `FAILED_SAFE_TO_RETRY`,
   `FAILED_TERMINAL` e `BLOCKED_BY_HUMAN`. O frontend recebe somente a projeção
   segura do estado.
4. **Conversation gate**: lease/claim persistente por contato e sessão, com
   heartbeat ou renovação enquanto uma chamada remota estiver ativa. Timeout
   ambíguo não libera o gate; somente conclusão ou encerramento remoto comprovado
   o libera.
5. **Hermes failure classification**: o gateway informa a fase da falha. Falhas
   antes do dispatch podem ser seguras; read timeout, reset, `502/504`, `5xx` sem
   garantia e corpo inválido após dispatch entram em conciliação.
6. **Hermes reconciliation**: registra um checkpoint de histórico anterior à
   chamada e procura uma nova mensagem de assistente. A finalização reutiliza o
   mesmo turno, correlação e eventos; uma corrida entre resposta direta e
   reconciliador é resolvida por append idempotente.
7. **Chat tracker**: usa a projeção como autoridade, continua polling enquanto o
   turno não for terminal, diferencia demora de falha e só mostra retry quando a
   projeção informar `retryAllowed=true`.

### Invariantes de implementação

- `eventId` único representa uma única entrada lógica.
- No máximo uma execução Hermes ativa ou incerta existe por contato/sessão.
- Um timeout, reload, erro de polling ou lease expirada não habilita retry.
- Mensagens posteriores são aceitas e permanecem ordenadas; nunca ultrapassam um
  predecessor ativo ou incerto.
- Uma saída canônica por turno é protegida por identidade determinística e
  persistência idempotente.
- Estado técnico não cria mensagem `URBA`.
- Conversas diferentes não compartilham um lock global.

## Project Structure

### Documentation (this feature)

```text
specs/003-hermes-turn-resilience/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── poc-conversation-projection.md
└── tasks.md
```

### Source Code (repository root)

```text
apps/urbana-connect-api/src/main/java/br/com/urbana/connect/
├── domain/reception/model/              # estados, turnos e eventos
├── domain/reception/port/out/            # portas Mongo/Hermes
├── application/reception/                # ingress durável, worker, orquestração
├── infrastructure/hermes/               # classificação e histórico HTTP
├── infrastructure/persistence/mongodb/reception/
│   └──                                  # documentos, índices e claims
└── interfaces/rest/poc/                  # projeção/recibo local

apps/urbana-connect-api/src/test/java/br/com/urbana/connect/
├── application/reception/
├── infrastructure/hermes/
├── infrastructure/persistence/mongodb/reception/
└── interfaces/rest/poc/

apps/poc-chat/src/
├── api/                                  # contrato seguro da projeção
├── state/                                # polling e estados visuais
└── components/                           # espera, demora e falha

apps/poc-chat/e2e/                             # cenários manuais controlados/live
```

**Structure Decision**: manter as mudanças dentro dos módulos existentes do
backend, da persistência POC e do chat. MongoDB é reutilizado como durable queue;
nenhum projeto, broker ou serviço externo novo será criado.

## Complexity Tracking

Nenhuma violação constitucional. A fila durável e o executor local são necessários
para cumprir o aceite antes do `202`, sobreviver a reload/restart e evitar
head-of-line blocking; são componentes da POC existente, não nova infraestrutura
operacional.

## Validation gates

1. Testes novos falham antes das mudanças de produção.
2. Backend focado e suíte completa passam com Java 21.
3. Frontend unit/integration, build e E2E determinístico passam.
4. Smoke Hermes → provedor é executado separadamente e seu resultado é
   classificado; falha externa não será simulada como sucesso.
5. Com serviços locais ativos, o E2E controlado demonstra resposta atrasada,
   timeout ambíguo, zero concorrência, retomada e resposta única.
6. `tasks.md` chega a 100% somente após todas as evidências acima.

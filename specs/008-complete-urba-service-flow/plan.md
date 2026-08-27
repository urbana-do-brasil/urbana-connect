# Implementation Plan: Atendimento comercial completo e seguro da Urba

**Branch**: `feature/008-complete-urba-service-flow` | **Date**: 2026-08-26 | **Spec**: [spec.md](./spec.md)

**Status**: implementação local delegada concluída e QA focado independente
aprovado; aceite live, PR para `hml` e promoção continuam pendentes por
bloqueio ambiental/aceite manual.

## Summary

Materializar o catálogo operacional da Urba e o fluxo comercial refinado no
runtime Hermes/Reception, incluindo o checkpoint conversacional de
enriquecimento de lead antes dos termos. O Hermes continuará responsável pela
interpretação e redação; o backend continuará autoridade para catálogo, estados,
aceite, pagamento, briefing, ownership e transcript. O ICP será guiado pelo
SOUL, persistido como fato explícito e observado quando for ignorado, sem um
hard gate de backend que possa travar a conversa.

O gate de reconciliação descrito em `baseline.md` foi concluído e o delta foi
dividido em três frentes com escrita separada: (A) fatos/persistência e
catálogo comercial; (B) SOUL, contexto integral, observabilidade e integração
de turnos; (C) POC/E2E e evidências. Os escritores pararam e o QA independente
confirmou os contratos locais; permanecem os gates de runtime live e aceite
manual antes do PR para `hml`.

## Technical Context

**Language/Version**: Java 21 LTS; Python do plugin Hermes; TypeScript 5.x

**Primary Dependencies**: Spring Boot 3.4.13, Gradle 8.x, MongoDB, Hermes Agent Sessions API, OpenRouter, React 19.2, Vite 8, Playwright

**Storage**: MongoDB para transcript, fatos/versionamento, contratação,
catálogo, ledger e projeções; SQLite interno do Hermes somente para a sessão
conversacional.

**Testing**: JUnit 5, Spring Boot Test, Testcontainers MongoDB, unittest do
plugin, Vitest/React Testing Library e Playwright.

**Target Platform**: backend containerizado e POC local; promoção posterior por
`feature/* -> hml -> main`.

**Project Type**: monorepo com backend web-service, adaptador/plugin Hermes e
frontend POC para teste manual.

**Performance Goals**:

- o primeiro turno elegível deve iniciar o checkpoint de ICP antes de preparar
  termos quando houver campos ausentes;
- um checkpoint concluído deve permitir avanço automático sem turno de
  confirmação redundante;
- handoff e retomada devem ser idempotentes e não duplicar mensagens;
- nenhum turno de retomada pode publicar resposta antes de sincronizar o
  transcript completo da thread atual.

**Constraints**:

- respeitar Clean Architecture e manter decisões de negócio no domínio/
  aplicação;
- manter JaCoCo mínimo de 60% e testes relevantes executados antes da aceitação;
- não criar hard gate backend específico para ICP;
- não criar `ICPCheckpointState`, contador de tentativas ou máquina de estado de
  diálogo no backend para comandar a coleta;
- não duplicar o catálogo no SOUL, plugin ou frontend;
- não filtrar do Hermes a thread atual por causa do ICP;
- não registrar valores brutos do ICP em logs técnicos;
- não incluir segredo, link comercial real ou ação produtiva em fixture local;
- nenhuma mensagem visível pode revelar detalhes técnicos;
- preservar todas as alterações preexistentes fora do escopo.

**Scale/Scope**: quatro serviços, perfil de ICP global por cliente, uma
contratação independente por ambiente/serviço, uma thread atual por sessão
Hermes e testes de duplicidade/reconciliação no bounded context `reception`.

## Constitution Check

*GATE: Must pass before implementation planning is accepted. Re-check after
design and before implementation.*

- **I — Stack oficial:** PASS. O plano usa Java 21, Spring Boot, Gradle e
  MongoDB já adotados; não cria mudança de stack.
- **II — Clean Architecture:** PASS. Catálogo, fatos, estados e regras ficam no
  domínio/aplicação; controllers, plugin e React apenas adaptam contratos.
- **III — Specification/Test First:** PASS WITH REMEDIATION. Existe código local
  preexistente e não verificado; ele será tratado como baseline e não como
  tarefa concluída. Antes de qualquer novo código, o gate reconcilia o diff com
  a spec e exige teste falhando primeiro para todo comportamento ainda ausente.
- **IV — Quality gate:** PASS WITH GATE. O aceite exige testes focalizados,
  regressão backend, testes do plugin/POC e JaCoCo mínimo de 60%; falhas de
  ambiente serão separadas de falhas de produto.
- **V — Homolog first:** PASS. Este ciclo produz documentação e depois validação
  local; não faz deploy nem promoção para `main`.
- **Delivery Workflow:** PASS WITH GATE. A subtarefa Jira e a branch
  `feature/008-complete-urba-service-flow` devem existir antes do primeiro
  escritor; o PR será aberto para `hml` e moverá a issue para
  `Awaiting approval`. Nenhuma dessas ações externas ocorre nesta revisão.

## Project Structure

```text
specs/008-complete-urba-service-flow/
├── spec.md
├── plan.md
├── baseline.md
├── research.md
├── data-model.md
├── contracts/
├── quickstart.md
└── tasks.md

apps/urbana-connect-api/
├── src/main/java/br/com/urbana/connect/
│   ├── domain/
│   ├── application/
│   ├── infrastructure/
│   └── interfaces/
└── src/test/java/br/com/urbana/connect/

integrations/hermes-agent/
├── profile/SOUL.md
└── plugins/urbana-domain/

apps/poc-chat/
infra/local-poc/
```

**Structure Decision**: manter o catálogo e os estados nas camadas existentes
de `apps/urbana-connect-api/`; manter orientação conversacional e transporte no
adaptador Hermes; manter apenas controles e fixtures não comerciais em
`apps/poc-chat/` e `infra/local-poc/`. Nenhum catálogo paralelo será criado.

## Design Decisions

### 1. ICP conversacional sem hard gate

O SOUL deve orientar o Hermes a iniciar o checkpoint após serviço confirmado e
intenção explícita, perguntar somente campos ausentes, repetir uma vez e marcar
`NÃO INFORMADO` quando necessário. O backend não rejeita `prepare_terms` por
ICP incompleto. Um evento `ICP_SKIPPED_BEFORE_TERMS` torna o desvio observável e
o E2E deve falhar semanticamente, sem transformar a ocorrência em erro visível.

### 2. Fatos globais e atualização silenciosa

Os três campos usam o modelo de fatos/versionamento existente. O perfil é
reutilizado entre os quatro serviços. Apenas declarações explícitas do cliente
podem criar/alterar fatos; o valor mais recente substitui o atual, inclusive
`NÃO INFORMADO`, sem nova mensagem para o cliente. O histórico permanece para
auditoria, mas o comportamento deve seguir a declaração mais recente.

### 3. Contexto integral da thread atual

Na retomada ou em qualquer turno que reidrate a conversa, o Hermes recebe a
thread atual completa — cliente, Urba, arquiteta e mensagens de sistema
necessárias — além do contexto associado do cliente. Threads anteriores inteiras
não são anexadas. Nenhuma projeção específica do ICP deve podar o transcript.

### 4. Proteção de dados em observabilidade

O fato persistido pode conter o texto necessário para a conversa e seu
versionamento. Eventos, logs e métricas carregam somente campo, status, origem,
conversa/turno e momento; nunca repetem o valor bruto do ICP.

### 5. Controle conversacional no SOUL, sem máquina de estado no backend

O SOUL interpreta a thread atual, os fatos correntes e o estágio comercial para
decidir pergunta inicial, segunda oportunidade, pausa por assunto paralelo ou
handoff e retomada dos campos ausentes. O backend não introduz
`ICPCheckpointState`, `attemptsByField` nem qualquer estado autoritativo que
comande o diálogo. Ele persiste somente os fatos explícitos e produz o evento
idempotente de desvio com conversa/turno/serviço, campos ausentes, ponto de
detecção e momento, sem valor bruto ou conteúdo da conversa.

### 6. Baseline local antes do delta

O código já modificado é insumo da execução, não evidência de conclusão. O gate
inicial inventaria e testa cada slice, preserva alterações de terceiros, resolve
entradas suspeitas sem exclusão automática e só então classifica as tarefas como
aproveitadas, incompletas, conflitantes ou fora de escopo. Novos escritores
recebem apenas o delta ainda necessário e um conjunto de arquivos exclusivo.

## Workstreams and Ownership

### Gate 0 — reconciliação e governança

**Owner**: Tech Lead Orchestrator, sem delegação de ambiguidade e sem escrita de
código de produto.

Responsabilidades:

- vincular/criar a subtarefa Jira sob PEE-23 somente após o GO de execução;
- preservar o snapshot e migrar/renomear a branch para
  `feature/008-complete-urba-service-flow`, mantendo `hml` como ancestral;
- mapear o diff existente aos requisitos e executar os testes focados;
- classificar cada slice e resolver o seeder suspeito de forma não destrutiva;
- atualizar `baseline.md` e os checkboxes somente com evidência.

### Workstream A — fatos, catálogo e contratos de domínio

**Owner**: Developer, com escrita exclusiva nos modelos/políticas de
`apps/urbana-connect-api/` e testes de domínio correspondentes.

Responsabilidades:

- consolidar o catálogo dos quatro serviços como fonte única;
- manter `PRONOUN_PREFERENCE`, `FIRST_TIME_HIRING` e `OCCUPATION` como fatos
  explícitos, globais e versionados;
- representar `NÃO INFORMADO` como estado concluído;
- garantir atualização silenciosa pelo valor explícito mais recente;
- manter as proteções reais de termos, aceite, pagamento e briefing;
- expor ao contexto autorizado quais campos estão presentes/ausentes, sem
  transportar controle de tentativa para o backend.

Arquivos prováveis:

- `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/CommercialPolicyService.java`
- `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/domain/reception/model/`
- `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/domain/servicecatalog/`
- `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/infrastructure/persistence/mongodb/`
- testes Java correspondentes.

### Workstream B — SOUL, contexto, handoff e observabilidade

**Owner**: Staff Engineer ou Developer sênior, com escrita exclusiva no
orquestrador/reconciliação, adaptador Hermes, plugin e perfil SOUL.

Responsabilidades:

- inserir no SOUL o fluxo de identificação, coleta dinâmica e avanço do ICP;
- fazer o SOUL reconhecer pela thread a pergunta inicial e a única segunda
  oportunidade, inclusive após assunto paralelo ou retorno humano;
- garantir captura incidental explícita sem inferência ou confirmação técnica ao
  cliente;
- manter a íntegra da thread atual no contexto enviado ao Hermes;
- orientar precedência pela declaração explícita mais recente do cliente;
- adicionar `ICP_SKIPPED_BEFORE_TERMS` idempotente, testar payload sem valores e
  manter o resultado de termos inalterado;
- preservar handoff visível, silêncio humano, retomada proativa e sanitização de
  falhas já definidos pela feature;
- incluir estado atual/pendente do ICP no resumo interno de handoff, sem expor
  resumo ao cliente.

Arquivos prováveis:

- `integrations/hermes-agent/profile/SOUL.md`
- `integrations/hermes-agent/plugins/urbana-domain/tools.py`
- `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ReceptionOrchestrator.java`
- `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ReceptionTurnReconciliationService.java`
- `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/tools/`
- testes de boundary, plugin e reconciliação.

### Workstream C — POC e E2E de qualidade conversacional

**Owner**: Developer, com escrita exclusiva em `apps/poc-chat/`, fixtures e
documentação operacional local.

Responsabilidades:

- ampliar o E2E semântico para verificar o checkpoint ICP antes dos termos;
- cobrir prompt dinâmico, parcial, recusa, segunda ausência, captura incidental,
  atualização silenciosa e handoff/retomada;
- preservar transcript integral, ownership, ack e controles da arquiteta;
- manter links e ações de teste inequivocamente não comerciais;
- registrar score, violações, transcript e referência à evidência separada do
  evento de ICP ignorado para revisão.
- manter em `quality-chat.spec.ts` somente caminhos conversacionais normais de
  SC-011; a injeção controlada de SC-014 permanece em teste backend separado e
  entra no pacote de evidência sem simular uma conversa de cliente.

### QA independente

Depois de A/B/C pararem de escrever, QA executará testes focalizados, regressão,
matriz de edge cases e os roteiros manuais. QA não aceitará a própria alteração.
Correções retornam ao escritor do arquivo, no máximo por duas iterações para a
mesma causa.

## Dependency Order

```text
Gate 0 — Jira/branch/baseline
              └── contratos/testes first
                   ├── A — fatos e catálogo ──────┐
                   ├── B — SOUL/contexto/boundary ├── C — estabilizar POC/E2E ── QA ── aceite local
                   └── C — E2E normal falhando ───┘
```

1. Com GO explícito, vincular Jira, preparar a branch `feature/*` e reconciliar
   o baseline sem reverter alterações preexistentes.
2. Congelar o mapa do delta e escrever/ajustar primeiro os testes de A/B que
   demonstrem comportamento ainda ausente.
3. Completar fatos/catalogo e, em escopo de escrita disjunto, SOUL,
   observabilidade e integração de contexto/handoff.
4. Escrever e estabilizar os cenários de `quality-chat.spec.ts` depois que os
   contratos de A/B estiverem estáveis; o cenário deve falhar antes da correção
   comportamental correspondente sempre que tecnicamente aplicável.
5. Parar todos os escritores e executar QA independente, suíte, quality gate e
   os cinco roteiros manuais.
6. Somente após aceite local abrir PR para `hml`, registrar evidências e mover o
   Jira para `Awaiting approval`; este plano não autoriza deploy nem promoção.

## Validation Matrix

| Área | Evidência mínima |
|---|---|
| Catálogo | quatro serviços, preços, limites, escopo, entregas e processo coerentes |
| ICP feliz | serviço/intenção → campos faltantes → respostas → termos |
| ICP parcial | segunda mensagem somente com campos faltantes |
| Recusa/ausência | `NÃO INFORMADO`, avanço e nenhuma insistência |
| Captura incidental | fato explícito reutilizado sem nova pergunta |
| Atualização | valor mais recente substitui silenciosamente |
| Handoff | ack visível, resumo interno com ICP, silêncio automático |
| Retomada | thread atual integral, decisão humana respeitada, avanço proativo único |
| Segurança | zero frases internas visíveis; logs sem valor bruto |
| E2E normal | ICP antes dos termos, critérios semânticos, transcript integral e diagnóstico reproduzível |
| Injeção controlada | termos permanecem funcionais, um evento por chave, payload sem valor bruto e nenhuma exposição ao Hermes/cliente |
| Regressão | testes focados, suíte backend, plugin, frontend e JaCoCo ≥ 60% |

## Risks and Mitigations

- **Hermes ignora o checkpoint**: SOUL mais E2E semântico e evento
  `ICP_SKIPPED_BEFORE_TERMS`; não criar bloqueio que altere a conversa em
  produção.
- **Backend volta a comandar a conversa**: proibir estado/contador autoritativo
  de ICP; revisão arquitetural e testes devem falhar se ausência do perfil
  bloquear termos ou se a retomada depender desse estado.
- **Contexto antigo contradiz o atual**: manter transcript integral, orientar
  precedência pelo último relato explícito do cliente e persistir versões.
- **Dados pessoais em logs**: limitar observabilidade a status/metadados; revisar
  testes de serialização.
- **Spec/artefatos legados omitem o checkpoint de ICP**: o checklist e a
  análise cross-artifact devem procurar a ausência do novo contrato antes da
  execução.
- **Fixture local parecer recurso real**: manter marcação de teste e validação
  no quickstart/compose.
- **Diff local ser confundido com trabalho concluído**: Gate 0, `baseline.md`,
  testes antes de marcar checkbox e um único escritor por conjunto de arquivos.
- **Branch/Jira fora do fluxo oficial**: nenhum escritor começa até ticket
  vinculado, issue em `Em andamento` e branch `feature/*` baseada em `hml`.

## Complexity Tracking

Não há violação constitucional aberta após os gates. O design reutiliza o
modelo de fatos/versionamento e a infraestrutura de contexto já existentes,
sem criar snapshot adicional, novo serviço de catálogo ou máquina de estado do
ICP. A existência do baseline anterior à aceitação é tratada explicitamente
como dívida de reconciliação obrigatória antes de novo código.

# Requirements Checklist: Resiliência de turnos Hermes

**Purpose**: validar completude, clareza, consistência e mensurabilidade dos
requisitos da spec 003 antes da aceitação operacional.
**Created**: 2026-08-07
**Feature**: [spec.md](../spec.md)

Este checklist avalia a qualidade dos requisitos escritos. A evidência de
implementação e de runtime permanece em [tasks.md](../tasks.md).

## Completude dos requisitos

- [x] CHK001 Cada mensagem aceita possui requisito explícito de persistência durável antes do processamento remoto. [Completeness, Spec §FR-001]
- [x] CHK002 O ciclo de vida cobre fila, processamento, demora/conciliação, conclusão e falha. [Completeness, Spec §FR-002]
- [x] CHK003 A spec cobre concorrência por contato, ordenação de sucessores e paralelismo entre contatos. [Coverage, Spec §FR-004, §FR-006, §FR-014]
- [x] CHK004 A spec cobre recuperação após reload, perda de GET e reinício local. [Coverage, Spec §FR-009, §US5]
- [x] CHK005 A spec cobre falhas pré-dispatch, ambíguas e terminais sem depender de mensagem artificial. [Completeness, Spec §FR-007, §FR-010, §FR-012]

## Clareza e consistência

- [x] CHK006 “Estado terminal” é distinguido de espera, demora e conciliação, sem permitir que um timeout visual altere o estado canônico. [Clarity, Spec §FR-002, §FR-009]
- [x] CHK007 A regra de retry seguro está vinculada a `retryAllowed` informado pelo backend, e não a erro de transporte do navegador. [Clarity, Spec §FR-007, §FR-012]
- [x] CHK008 A identidade lógica `eventId` e a saída canônica única são requisitos explícitos e consistentes com a prevenção de duplicidade. [Consistency, Spec §FR-001, §FR-008, §FR-011]
- [x] CHK009 O escopo exclui WebSocket/SSE, webhook real, produção, credenciais, modelo e provedor, evitando ambiguidade de entrega. [Scope, Spec §FR-015, §6]
- [x] CHK010 A decisão de manter o polling como mecanismo de entrega está consistente entre contexto, plano e requisito de retomada. [Consistency, Spec §FR-003, §FR-009, plan.md §Design]

## Critérios de aceite e mensurabilidade

- [x] CHK011 Os critérios SC-001 a SC-005 definem unicidade, concorrência, isolamento e estados observáveis. [Measurability, Spec §SC-001–SC-005]
- [x] CHK012 O critério de observabilidade exige correlação e ausência de segredos, sem exigir conteúdo conversacional no navegador. [Measurability, Spec §FR-013, §SC-006]
- [x] CHK013 A spec separa o smoke Hermes → provedor do E2E da aplicação e define como classificar falha externa. [Clarity, Spec §SC-008]
- [x] CHK014 O objetivo de aceite em até 2s e o polling com backoff estão registrados como metas técnicas, não como prazo terminal de UI. [Consistency, plan.md §Technical Context; Spec §FR-009]

## Cenários de exceção e recuperação

- [x] CHK015 Timeout após dispatch, perda de resposta e resposta posterior estão descritos como cenários distintos de reconciliação. [Coverage, Spec §US3, §Edge Cases]
- [x] CHK016 A expiração de lease durante chamada remota e o reinício do processo estão explicitamente tratados. [Edge Case, Spec §Edge Cases]
- [x] CHK017 Retransmissão do mesmo evento e corrida entre resposta direta e reconciliador estão cobertas por requisitos de idempotência. [Coverage, Spec §FR-008, §FR-011, §Edge Cases]
- [x] CHK018 Indisponibilidade de chaves ou do provedor é reconhecida como condição ambiental, sem aprovação simulada. [Dependency, Spec §SC-008, §Edge Cases]

## Dependências, riscos e rastreabilidade

- [x] CHK019 MongoDB é identificado como fonte durável e SQLite interno do Hermes permanece fora da Urbana. [Dependency, plan.md §Technical Context; research.md Decision 1]
- [x] CHK020 Timeout Hermes, lease, claim, heartbeat e polling são configuráveis e a necessidade de calibragem está documentada. [Clarity, research.md Decision 6]
- [x] CHK021 Os artefatos definem que readiness, smoke contratual e testes determinísticos não substituem o E2E com evidência Mongo. [Traceability, quickstart.md §Validação; tasks.md §Evidências]
- [x] CHK022 As lacunas ambientais atuais estão registradas sem marcar como concluídas as tarefas T048, T050 e T053. [Integrity, tasks.md §Evidências]

## Notes

- Os itens acima validam a redação e a cobertura dos requisitos; não são uma
  declaração de que o E2E Docker/Testcontainers foi concluído.
- O status operacional atual e os próximos passos obrigatórios permanecem em
  `tasks.md`.

# Specification Quality Checklist: Atendimento comercial completo e seguro da Urba

- **Purpose**: Validate specification completeness and quality before proceeding to planning
- **Created**: 2026-08-26
- **Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification
- [x] Spec, plan, data model, contracts, quickstart e tasks usam o mesmo
  contrato de checkpoint ICP
- [x] O plano cobre captura incidental, reutilização global, handoff/retomada e
  observabilidade sem conteúdo bruto
- [x] O controle de pergunta, segunda oportunidade, pausa e retomada do ICP fica
  no SOUL/thread; nenhum artefato exige máquina de estado autoritativa no backend
- [x] Existe tarefa explícita para escrever os cenários ICP/termos em
  `quality-chat.spec.ts` antes da correção comportamental correspondente
- [x] O evento `ICP_SKIPPED_BEFORE_TERMS` possui teste de idempotência, payload
  sem valores e resultado comercial inalterado
- [x] SC-011 diferencia fluxo conversacional normal da injeção controlada de
  SC-014
- [x] Baseline local, Jira, branch `feature/*`, QA independente e PR para `hml`
  possuem gates explícitos antes da execução/promoção

## Notes

- Validation iteration 3: checklist reavaliado após a correção de prontidão
  técnica; todos os itens permanecem atendidos.
- O contrato distingue checkpoint conversacional de hard gate: o ICP é coletado
  antes dos termos pelo SOUL, mas sua ausência não gera rejeição de backend.
- O plano registra as decisões técnicas sobre contexto integral, fatos
  versionados, observabilidade sem conteúdo bruto e E2E semântico.
- O baseline preexistente é tratado como não verificado; nenhuma tarefa é
  concluída pela mera presença de código e o Gate 0 precede novos escritores.
- `ICPCheckpointState` e contadores autoritativos foram removidos do desenho; o
  evento contém apenas correlação e campos ausentes.
- Recursos locais e controles humanos continuam sendo fixtures de validação,
  sem valor comercial.

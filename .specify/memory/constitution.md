<!--
Sync Impact Report
- Version change: template -> 1.0.0
- Established principles: stack oficial; Clean Architecture; SDD + TDD; quality gates; homolog-first flow
- Added sections: Stack and Constraints; Delivery Workflow; Governance
- Removed sections: generic placeholder sections from bootstrap template
- Templates requiring updates:
  - ✅ .specify/templates/spec-template.md
  - ✅ .specify/templates/plan-template.md
  - ✅ .specify/templates/tasks-template.md
  - ✅ .specify/scripts/bash/update-agent-context.sh
- Follow-up TODOs: nenhum
-->

# Urbana Connect Constitution

## Core Principles

### I. Stack Oficial e Coerência Tecnológica
O projeto MUST manter Java 21, Spring Boot 3.4.x, Gradle 8.x e MongoDB como baseline atual. Mudanças de stack não entram como detalhe incidental de feature; elas exigem decisão explícita, justificativa e impacto documentado.

### II. Clean Architecture como Estrutura Padrão
Toda feature MUST respeitar a separação entre `domain`, `application`, `infrastructure` e `interfaces`. Regras de negócio não podem nascer em controllers ou em integrações externas, e dependências concretas MUST permanecer nas bordas.

### III. Specification-First e Test-First
Features de negócio MUST começar com spec antes da implementação. Comportamento novo MUST ter teste escrito antes do código correspondente sempre que tecnicamente aplicável. A spec define o contrato macro; os testes definem o contrato executável micro.

### IV. Qualidade Automatizada é Gate
Build verde e cobertura mínima não são opcionais. O projeto MUST manter quality gate automatizado com JaCoCo mínimo de 60% e testes relevantes executados antes da promoção de uma entrega. Cobertura é piso, não substituto de teste bom.

### V. Homolog Primeiro, Main Depois
O fluxo padrão MUST ser `feature -> hml -> validação em homolog -> main`. A branch `hml` é o ambiente de integração e validação operacional; a `main` é o ramo mais protegido e estável. Nenhuma feature deve assumir promoção direta para `main` sem validação prévia em homolog, salvo decisão explícita.

## Stack and Constraints

- Linguagem: Java 21 LTS
- Framework principal: Spring Boot 3.4.x
- Build: Gradle
- Testes: JUnit 5, Spring Boot Test, Testcontainers
- Runtime de homolog: k3s em VPS Contabo
- Registry: GHCR
- Manifestos: Kustomize
- Observabilidade inicial: Prometheus, Grafana, Loki, Promtail
- Secrets reais MUST permanecer fora do repositório

## Delivery Workflow

- toda subtarefa MUST ser movida para `Em andamento` ao começar
- implementação concluída + PR aberto MUST mover a issue para `Awaiting approval`
- merge aprovado MUST mover a issue para `Concluído`
- PRs MUST deixar explícitos objetivo, resumo da mudança, validação executada e ticket Jira
- Jira e GitHub são o registro formal da execução, não apenas o chat

## Governance

- Esta constituição orienta specs, planos, tarefas e reviews do repositório
- Emendas MUST atualizar este arquivo e os templates dependentes na `.specify/`
- Alterações materiais de princípio seguem versionamento semântico:
  - MAJOR: quebra de princípio ou mudança incompatível de fluxo
  - MINOR: novo princípio ou nova exigência material
  - PATCH: clarificações sem mudança de comportamento
- Revisões de PR MUST verificar aderência a esta constituição quando a mudança tocar arquitetura, fluxo, testes, deploy ou governança

**Version**: 1.0.0 | **Ratified**: 2026-04-01 | **Last Amended**: 2026-04-01

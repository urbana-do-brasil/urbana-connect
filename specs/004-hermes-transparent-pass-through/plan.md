# Implementation Plan: Hermes Transparent Pass-through

**Branch**: `003-hermes-turn-resilience`
**Consolidated validation branch**: `feat/pee-101`
**Date**: 2026-08-07
**Spec**: [spec.md](./spec.md)

## Summary

Simplificar o caminho de conteúdo de `ReceptionOrchestrator` para transportar a
mensagem atual ao Hermes e transportar de volta o texto do Hermes sem mutações.
A camada de entrega da spec 003 permanece intacta. O contrato de conversação do
profile deixa de exigir JSON/ações para o diálogo normal; controles de ação
operacional continuam nas bordas que os autorizam.

## Contexto técnico

- Java 21, Spring Boot e Gradle no backend;
- Hermes Sessions API e profile em `integrations/hermes-agent/profile/`;
- MongoDB como transcript/projeção canônica;
- React/TypeScript como consumidor da projeção existente;
- testes JUnit/AssertJ, Vitest/RTL e Playwright;
- Docker Compose local somente para validação, sem produção.

## Estratégia de execução

1. Criar testes que expressem o contrato de round-trip literal e falhem contra
   o prefixo/fallback/wrapper atuais.
2. Refatorar um único escritor no conjunto sobreposto do backend, modelos,
   testes diretamente relacionados e profile Hermes.
3. Preservar a camada assíncrona da spec 003 e adaptar apenas tipos/branches que
   dependem do contrato antigo de `AgentOutput`.
4. Rodar revisão e testes independentes depois que o escritor parar.
5. Revalidar serviços locais, emitir uma mensagem de controle com conteúdo
   distintivo e comparar request Hermes, histórico Mongo e projeção/UI.

## Limites de responsabilidade

- `ReceptionOrchestrator` continua orquestrando ciclo de vida, mas não conversa
  em nome do Hermes.
- `HttpHermesSessionsGateway` preserva correlação e contrato de transporte.
- `AgentOutputReconciler`, `ReceptionResponsePolicy` e `NonProspectPolicy` só
  permanecem se algum controle operacional realmente depender deles; não devem
  participar do texto normal.
- O frontend recebe e renderiza a projeção existente. Só será alterado se a
  implementação atual fizer transformação textual comprovada.

## Riscos e mitigação

- O tipo `AgentOutput` pode estar compartilhado com ações de ferramenta; separar
  texto conversacional de autorização operacional antes de apagar tipos.
- Testes antigos podem codificar o comportamento contaminado; substituir apenas
  expectativas incompatíveis com a spec aprovada, preservando testes de entrega.
- O Hermes pode retornar envelope JSON legado; preservar `message` literal e
  validar o profile contra a nova regra, sem inventar fallback.
- O ambiente local pode ter latência/indisponibilidade externa; classificar a
  evidência como bloqueio ambiental, sem simular sucesso.

# Feature Specification: [FEATURE NAME]

**Feature Branch**: `[###-feature-name]`  
**Created**: [DATE]  
**Status**: Draft  
**Input**: User description: "$ARGUMENTS"

## Metadados

- `Título da feature`:
- `Ticket Jira`:
- `Responsável pela spec`:
- `Contexto de branch`: `feature/* -> hml -> main`

## 1. Contexto

Descreva o problema, por que esta feature existe e onde ela se encaixa no fluxo da Urba.

Perguntas obrigatórias:

- qual necessidade do sistema ou do negócio está sendo atendida?
- o que já existe hoje?
- o que ainda está faltando?
- há dependência operacional de homolog, secrets, integrações ou infra?

## 2. Comportamentos esperados

Liste apenas comportamento observável e verificável.

Formato recomendado:

1. Dado `[estado inicial]`, quando `[ação/evento]`, então o sistema deve `[resultado]`.
2. Dado `[estado inicial]`, quando `[ação/evento]`, então o sistema deve `[resultado]`.

## 3. Critérios de aceite

Defina as condições mínimas para considerar a feature pronta.

Os critérios MUST:

- ser verificáveis
- ser suficientes para review e homolog
- cobrir comportamento, segurança e operação quando aplicável

## 4. Edge Cases

Liste cenários de borda, falhas e restrições importantes.

Exemplos:

- payload inválido
- dependência externa indisponível
- autenticação incorreta
- dados obrigatórios ausentes
- timeout, retry ou duplicidade

## 5. Observabilidade e validação

Descreva como a feature será validada e observada.

Cobrir quando fizer sentido:

- testes unitários
- testes de integração
- logs esperados
- métricas esperadas
- smoke test/manual test em homolog

## 6. Fora de escopo

Declare explicitamente o que esta feature não cobre.

## 7. Dúvidas em aberto

Liste decisões pendentes, suposições críticas ou pontos que precisam de confirmação antes da implementação.

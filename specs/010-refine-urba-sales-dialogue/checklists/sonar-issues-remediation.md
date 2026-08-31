# Checklist de qualidade — Issues Sonar do PR

**Feature**: [sonar-issues-remediation.md](../sonar-issues-remediation.md)
**Ticket**: PEE-106
**Criado**: 2026-08-31

## Contrato

- [x] A contagem e as regras foram extraídas da análise do PR.
- [x] O escopo diferencia refactor de produção e higiene dos testes.
- [x] O contrato proíbe supressões, exclusões e mudanças de quality gate.

## Implementação

- [x] Regex, construtor longo, constantes, atribuições e declarações de produção ajustados.
- [x] Lambdas e asserções dos testes refatoradas sem perda de verificações.
- [x] Testes de caracterização preservam aceite, reconciliação e guardrails.

## Validação

- [x] Suítes focadas verdes.
- [x] Suíte Gradle completa e JaCoCo verdes (JDK 21, 466 testes).
- [x] Cobertura de código novo permanece ≥ 80%: `606/698 = 86,82%` (linhas `369/390`, condições `237/308`).
- [ ] Issues Sonar do PR eliminados ou justificados.
- [x] Nenhuma alteração em plugin/frontend/configuração foi introduzida; regressões remotas serão confirmadas no PR.
- [x] `git diff --check` verde.

## Handoff

- [x] QA independente executada após o escritor parar (revisão do inventário e diff).
- [x] Contagem Sonar antes/depois, comandos, arquivos e riscos registrados; análise oficial pós-push pendente.

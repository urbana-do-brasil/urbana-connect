# Checklist de qualidade — Remediação da cobertura Sonar

**Feature**: [coverage-remediation.md](../coverage-remediation.md)
**Ticket**: PEE-106
**Criado**: 2026-08-31

## Contrato

- [x] O problema está quantificado com a análise do PR e o relatório JaCoCo.
- [x] A meta é explícita: cobertura de código novo do Sonar ≥ 80%.
- [x] O escopo preserva comportamento e prioriza testes.
- [x] O fora de escopo impede reduzir threshold ou esconder código.

## Cobertura comportamental

- [x] Orquestrador: aceite, auditoria de apresentação e fences.
- [x] Reconciliação: handoff, corridas, leases e fallbacks.
- [x] Aceite: validações, mismatches, CAS e idempotência.
- [x] Persistência: insert, duplicidade, CAS sem alteração e documentos legados.
- [x] Ferramentas/modelos: rejeições seguras e invariantes de borda.

## Validação

- [x] Suítes focadas verdes.
- [x] Suíte Gradle completa e JaCoCo verdes.
- [x] Cálculo contra `origin/hml` registrado: `595/699 = 85,12%` (linhas `363/387`, condições `232/312`).
- [ ] Check Sonar do PR ≥ 80%.
- [x] Plugin, contratos, isolamento e lint do frontend sem regressão observada.
- [x] `git diff --check` verde.

Nota: o teste/build completo do frontend não foi executado localmente porque o
ambiente não possui o binding nativo opcional do `rolldown`; o lint passou e a
validação equivalente do CI deverá fechar esse item.

## Handoff

- [x] Arquivos alterados e justificativas listados no handoff do writer/QA.
- [x] Comandos e resultados registrados no handoff do QA.
- [x] Critérios cobertos e riscos residuais descritos.
- [x] QA independente executada após o escritor parar.

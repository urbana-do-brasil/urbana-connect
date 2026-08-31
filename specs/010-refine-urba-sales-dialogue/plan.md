# Implementation Plan: Refinamento da conversa comercial da Urba

**Branch**: `010-refine-urba-sales-dialogue` | **Date**: 2026-08-27 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/010-refine-urba-sales-dialogue/spec.md`

## Summary

Ajustar a voz conversacional da Urba e o conteúdo apresentado a partir do
catálogo canônico, preservando os guardrails determinísticos do fluxo Hermes.
O trabalho combina uma revisão do `SOUL.md` com mudanças mínimas no catálogo e
nas mensagens/regras já existentes: reconhecimento de “Aceito” após termos,
orientação textual de uma unidade por ambiente e cópia completa da Decor
Reforma. O player de pagamento não será implementado; sua capacidade de
selecionar quantidade fica como TODO pré-homologação. A ordem e o aceite também
ganham uma evidência estruturada e imutável para que o critério de auditoria
seja verificável mesmo quando houver mais de uma unidade/ambiente.

## Technical Context

**Language/Version**: Java 21 LTS; Python 3 do runtime Hermes para testes do plugin; Markdown para o perfil
**Primary Dependencies**: Spring Boot 3.4.x, Gradle 8.x, JUnit 5, pytest/unittest do plugin, runtime Hermes existente
**Storage**: MongoDB existente, transcript/invocações e nova coleção aditiva `reception_terms_consent_audits`
**Testing**: JUnit 5, Spring Boot Test, testes do plugin Hermes, scripts de contrato/profile e suíte focalizada
**Target Platform**: backend em container e profile Hermes da POC local
**Project Type**: backend web-service + integração/profile Hermes
**Performance Goals**: manter os limites e a reconciliação de turnos existentes; nenhuma nova chamada externa
**Constraints**: Clean Architecture, TDD, JaCoCo mínimo de 60%, secrets fora do repositório, sem player real ou transação financeira
**Scale/Scope**: uma conversa por sessão; quatro serviços canônicos; mensagens sintéticas da POC

### Decisões de implementação

- `SOUL.md` permanece a fonte da personalidade, ritmo, vocabulário, emojis e
  orientação de quantidade; não deve conter estado interno ou formato
  estruturado de ferramenta.
- O catálogo canônico (`ServiceCatalogItem`) continua sendo a fonte de preço,
  área, entregas, suporte e exclusões. A apresentação da Decor Reforma deve
  carregar os fatos exigidos pela spec sem criar um quinto serviço ou links reais.
- A aceitação simples é uma regra determinística: o texto “Aceito” só vale
  depois que os termos foram apresentados no serviço/ambiente corrente. A forma
  combinada “Aceito, cartão” e o par consecutivo devem preservar a ordem.
- A mensagem retornada por `prepare_payment` recebe a orientação de quantidade
  e comprovante. A capacidade visual do player não é inferida nem implementada.
- A auditoria não pode depender de reconstrução heurística. Uma entidade
  `TermsConsentAudit` imutável preserva apresentação, aceite, texto exato,
  timestamps, recurso/versão dos termos, serviço e unidade de contratação.
  A transição `PRESENTED -> ACCEPTED` é condicional e idempotente; aceite só
  pode ser associado a uma apresentação já persistida.
- A unidade recebe um identificador opaco gerado pelo backend e vinculado à
  mensagem de origem que explicitou o ambiente. O identificador nunca é
  derivado apenas da etiqueta normalizada; quando a conversa não tornar uma
  unidade inequívoca, o fluxo pede esclarecimento antes de preparar termos.
- A atualização da conversa e a gravação do aceite devem compartilhar a
  autoridade CAS/transactional do Mongo quando o adaptador estiver ativo.
  Registros legados sem evidência estruturada não são retroativamente
  considerados auditados: devem reapresentar os termos antes de aceitar.

## Constitution Check

*GATE: pass before Phase 0 research; rechecked after design.*

- I. Stack oficial: **PASS** — nenhuma tecnologia nova.
- II. Clean Architecture: **PASS** — regras permanecem no domínio/aplicação;
  profile e catálogo ficam nas bordas apropriadas.
- III. Specification/Test-first: **PASS** — spec aprovada; testes de aceite
  serão escritos antes das alterações de código.
- IV. Qualidade automatizada: **PASS** — rodar testes focalizados, plugin/profile
  e suíte Gradle; JaCoCo existente permanece obrigatório.
- V. Homolog primeiro: **PASS** — esta execução não faz deploy; PR volta para
  `hml` após QA.

## Project Structure

### Documentation (this feature)

```text
specs/010-refine-urba-sales-dialogue/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # não aplicável: nenhum contrato externo novo
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)
```text
integrations/hermes-agent/profile/SOUL.md
integrations/hermes-agent/plugins/urbana-domain/
├── __init__.py
└── test_tools.py
apps/urbana-connect-api/src/main/java/br/com/urbana/connect/
├── domain/servicecatalog/model/ServiceCatalogItem.java
├── domain/reception/model/TermsConsentAudit.java
├── domain/reception/port/out/TermsConsentAuditGateway.java
├── application/reception/CommercialPolicyService.java
├── application/reception/TermsAcceptanceUseCase.java
└── application/reception/tools/StatefulDomainToolService.java
apps/urbana-connect-api/src/test/java/br/com/urbana/connect/
├── application/reception/CommercialPolicyServiceTest.java
├── application/reception/TermsAcceptanceUseCaseTest.java
├── infrastructure/persistence/mongodb/reception/MongoTermsConsentAuditGatewayTest.java
├── application/reception/tools/StatefulDomainToolServiceTest.java
└── domain/servicecatalog/model/ServiceCatalogItemTest.java
specs/010-refine-urba-sales-dialogue/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── tasks.md
```

**Structure Decision**: manter mudanças aderentes aos limites do monorepo:
`apps/urbana-connect-api/` e `integrations/hermes-agent/`, evitando fontes
paralelas, cópias de runtime externo, novos endpoints ou alterações no player.

## Fases de execução

1. Escrever testes que capturem aceite simples/composto, conteúdo da Decor
   Reforma, mensagem de quantidade, evidência estruturada e preservação dos
   guardrails.
2. Implementar catálogo/cópia, evidência de termos e política determinística
   mínima.
3. Atualizar o profile `SOUL.md` e a descrição do plugin sem ampliar a superfície
   de ferramentas.
4. Rodar testes focados, scripts de contrato/profile e regressões Gradle.
5. Executar QA independente, revisar a matriz da spec e preparar o handoff para
   o PR em `hml`. Validação frontend/corpus é evidência posterior, não player.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Coleção aditiva de auditoria de aceite | CA-011 exige recuperar serviço, ambiente, recurso, instantes e texto exato mesmo quando há mais de uma contratação; a evidência não é reconstruível de forma segura apenas pelo transcript | Reusar somente `NEED`/estado da conversa perderia a associação entre aceite e unidade e permitiria retrovalidar registros legados |

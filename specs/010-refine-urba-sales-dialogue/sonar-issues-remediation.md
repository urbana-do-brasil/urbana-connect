# Especificação — Remediação dos issues Sonar do PR

**Feature**: `010-refine-urba-sales-dialogue`
**Ticket Jira**: `PEE-106` (subtask de `PEE-23`)
**PR**: `#63`
**Branch**: `010-refine-urba-sales-dialogue` → `hml`
**Status**: aprovado para execução
**Data**: 2026-08-31

## 1. Contexto

Depois da remediação de cobertura, o quality gate do PR passou, mas a análise
do SonarCloud listou 52 issues abertos pela API do PR (o painel visual foi
reportado como 53). A lista contém principalmente `java:S5778` em testes
recém-adicionados, além de alertas de manutenção em código de produção e alguns
testes preexistentes.

Inventário observado:

| Regra | Quantidade | Escopo |
| --- | ---: | --- |
| `java:S5778` | 31 | lambdas de asserção com mais de uma chamada potencialmente lançável |
| `java:S1659` | 6 | múltiplos campos declarados na mesma linha |
| `java:S4165` | 4 | atribuições redundantes no construtor compacto do record |
| `java:S5853` | 2 | asserções consecutivas que podem formar uma cadeia |
| `java:S8786` | 2 | regex de aceite com backtracking superlinear |
| `java:S1192` | 2 | literais `ENVIRONMENT` e `customerMessage` duplicados |
| `java:S1858` | 2 | `toString()` desnecessário em valor já textual |
| `java:S107` | 1 | construtor de reconciliação com nove parâmetros |
| `java:S5961` | 1 | método de teste com 27 asserções |
| `java:S1144` | 1 | método privado `sourceText` não utilizado |

## 2. Objetivo

Eliminar os issues acionáveis mantendo o comportamento comercial, os
guardrails de aceite/pagamento/handoff, a superfície Hermes e a cobertura já
conquistada. As mudanças devem ser refactors semânticos e testes de
caracterização, não supressões ou alterações artificiais no quality gate.

## 3. Escopo de implementação

### 3.1 Produção

1. Reescrever a detecção de aceite em `CommercialPolicyService` para evitar as
   regexes superlineares, preservando os casos positivos e negativos já
   cobertos.
2. Substituir o construtor de nove parâmetros de
   `ReceptionTurnReconciliationService` por uma dependência agrupada, mantendo
   os construtores compatíveis de uso simples e atualizando a configuração e
   os testes.
3. Extrair constantes para `ENVIRONMENT` e `customerMessage` em
   `StatefulDomainToolService` e remover o overload privado `sourceText` sem
   uso.
4. Remover somente as atribuições redundantes do construtor compacto de
   `TermsConsentAudit`; as validações e invariantes devem permanecer.
5. Declarar cada campo de `TermsConsentAuditDocument` em sua própria linha.

### 3.2 Testes

1. Refatorar lambdas `assertThatThrownBy` para que cada lambda contenha uma
   única chamada potencialmente lançável, pré-calculando argumentos e doubles.
2. Consolidar asserções consecutivas sobre o mesmo objeto em uma única cadeia
   quando isso não reduzir a clareza.
3. Dividir o teste de configuração que excede o limite de asserções em métodos
   focados, sem remover verificações.
4. Remover `toString()` desnecessário nos testes.
5. Preservar ou ampliar testes de caracterização para os casos de aceite,
   reconciliação e fallback afetados pelo refactor.

## 4. Critérios de aceite

- [ ] O novo check SonarCloud do PR não apresenta os 52 issues inventariados;
  qualquer ocorrência residual deve ser justificada por uma limitação real e
  não por supressão.
- [ ] `./gradlew --no-daemon --max-workers=1 clean test jacocoTestReport` passa
  com JDK 21 e a cobertura de código novo permanece acima de 80%.
- [ ] Os testes focados de política comercial, reconciliação, aceite,
  persistência, ferramentas, configuração e modelos passam isoladamente.
- [ ] A semântica de `isExplicitTermsAcceptance`, fallbacks de reconciliação,
  evidência durável, handoff e pagamento permanece coberta e inalterada.
- [ ] Não há alteração em `SOUL.md`, integrações Hermes, threshold/exclusões do
  Sonar ou comportamento comercial fora do necessário para o refactor.
- [ ] `git diff --check` passa e o diff contém apenas produção/testes/spec desta
  remediação; alterações preexistentes fora do escopo permanecem intocadas.
- [ ] QA independente revisa o diff, executa os testes e confirma o resultado
  do Sonar antes do push final.

## 5. Fora de escopo

- Alterar catálogo, preços, mensagens comerciais, termos, pagamento ou fluxo de
  handoff.
- Adicionar exclusões, `NOSONAR`, mudanças de severidade ou redução de regras.
- Reescrever a suíte sem relação com os issues identificados.
- Fazer merge do PR ou promover a `main`.

## 6. Validação esperada

O handoff deve listar a contagem de issues antes/depois por regra, os comandos
executados, a cobertura antes/depois, eventuais limitações ambientais e os
checks remotos do PR. A análise oficial do SonarCloud no próprio PR é a fonte
final para a contagem de issues.

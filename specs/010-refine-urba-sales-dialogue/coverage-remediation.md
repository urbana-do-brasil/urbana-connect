# Especificação — Remediação da cobertura do código novo

**Feature**: `010-refine-urba-sales-dialogue`
**Ticket Jira**: `PEE-106` (subtask de `PEE-23`)
**PR**: `#63`
**Branch**: `010-refine-urba-sales-dialogue` → `hml`
**Status**: aprovado para execução
**Data**: 2026-08-31

## 1. Contexto

O PR #63 tem o build, os testes do backend e os testes do frontend aprovados,
mas o quality gate do SonarCloud falha com **64,3% de cobertura no código novo**
quando o projeto exige **80%**. O gate JaCoCo do Gradle continua sendo um piso de
60% de linhas e não substitui o gate de código novo do Sonar.

O diagnóstico do relatório gerado pelo PR mostrou que a métrica nova combina
linhas e condições/branches:

| Medida do código novo | Coberto | Total |
| --- | ---: | ---: |
| Linhas executáveis | 290 | 387 |
| Condições/branches | 159 | 312 |
| Métrica combinada | 449 | 699 |

São necessários pelo menos mais 111 elementos cobertos para atingir 80%. A
maior parte do gap está no código Java de auditoria de aceite, reconciliação de
turnos, persistência Mongo e rejeições seguras introduzido pela feature. O
`SOUL.md` e os demais arquivos Markdown não são a causa desse indicador.

## 2. Objetivo

Complementar a suíte automatizada para que o código novo do PR alcance cobertura
SonarCloud de pelo menos 80%, preservando integralmente o comportamento comercial,
os guardrails de segurança e o contrato Hermes já validados.

A implementação desta remediação deve ser centrada em testes. Não deve alterar
regras de negócio, integrações, o perfil da Urba ou o threshold/configuração do
Sonar apenas para fazer o indicador passar.

## 3. Comportamentos e cenários obrigatórios

### 3.1 Orquestração e publicação

1. Dado um aceite recebido após uma apresentação durável de termos, quando o
   turno é processado, então o aceite é associado à mensagem inbound exata,
   registrado na auditoria e a conversa avança uma única vez.
2. Dado um `TermsAcceptanceUseCase` ausente, uma conversa sem unidade/ambiente,
   uma invocação inexistente ou uma URL que não aparece na mensagem publicada,
   quando a apresentação é reconciliada, então nenhum aceite é ativado e a
   conversa permanece segura.
3. Dado um inbound mais novo persistido enquanto um turno antigo ainda está
   gerando ou publicando sua resposta, então o turno antigo fica retryable,
   nenhuma resposta obsoleta é considerada a resposta final e a liberação do
   lease ocorre de modo seguro.
4. Dado um turno que não possui inbound mais novo, quando sua resposta é
   publicada, então a mensagem é idempotente, a auditoria de termos é registrada
   quando aplicável e o turno é concluído normalmente.

### 3.2 Reconciliação e fallback comercial

1. Dado um turno em reconciliação, quando a conversa está sob atendimento humano,
   então somente o ack de handoff é publicado, com e sem ack pré-existente e com
   e sem lease.
2. Dado um inbound novo antes da reconciliação, antes da primeira publicação ou
   durante a publicação, então cada fence correspondente deixa o turno seguro
   para retry sem publicar texto tardio.
3. Dado um candidato Hermes rejeitado pela política, então o fallback é correto
   para `NOT_STARTED`, `PREPARED`, `PROOF_RECEIVED`, `CONFIRMED` e `REJECTED`,
   sem confirmar pagamento ou liberar briefing indevidamente.
4. Dado texto Hermes válido ou inválido para a apresentação inicial, então a
   identidade da Urba é adicionada somente quando necessária.

### 3.3 Aceite e evidência durável

1. Entradas nulas, vazias, versões negativas, status diferente de `PRESENTED`,
   timestamps anteriores à apresentação e evidências ausentes devem falhar
   fechadas com erro determinístico.
2. Evidência de outra conversa, unidade ou serviço deve ser rejeitada.
3. Um primeiro aceite deve vencer; replays posteriores não podem sobrescrever o
   texto, evento, mensagem ou horário do primeiro aceite.
4. Tanto o caminho com gateway de conversa quanto o construtor de compatibilidade
   sem gateway devem permanecer cobertos.

### 3.4 Persistência e compatibilidade

1. O gateway de auditoria deve ser testado em inserção, registro já existente,
   corrida de chave duplicada, CAS bem-sucedido, CAS sem alteração, evidência
   ausente e evidência ainda não aceita.
2. O mapeamento de auditoria e de conversa deve preservar os novos campos.
3. Documentos legados sem `sourceMessageIds` devem recuperar o fallback para o
   `sourceMessageId` único; listas válidas devem ser preservadas.

### 3.5 Ferramentas e modelos

1. As rejeições seguras devem cobrir método de pagamento ausente/inválido,
   serviço não confirmado, ambiente não confirmado, termos sem evidência,
   handoff e a rejeição genérica.
2. A origem de fatos deve cobrir transcript ausente, evidência explícita,
   negação, alias de serviço, frase vazia e frase com limites de palavra.
3. Os invariantes de `ActiveTurnLease`, `ReceptionConversation` e
   `TermsConsentAudit` devem cobrir valores nulos/vazios, modos incompatíveis,
   versões inválidas, reabertura de aceite legado e replay idempotente.

## 4. Matriz de implementação e prioridade

Os testes devem priorizar os maiores gaps observados no relatório atual:

| Área | Cobertura combinada atual | Entrega esperada |
| --- | ---: | --- |
| `ReceptionOrchestrator` | 26/95 | testes de aceite durável, auditoria de apresentação e fences |
| `ReceptionTurnReconciliationService` | 99/157 | testes de corridas, handoff, leases e todos os fallbacks |
| `MongoTermsConsentAuditGateway` | 34/62 | testes de insert, CAS, duplicidade, ausência e mapeamento |
| `TermsAcceptanceUseCase` | 59/86 | testes de entradas inválidas e mismatches de evidência |
| `StatefulDomainToolService` | 77/104 | testes de rejeições, provenance e normalização |
| Modelos/adapters restantes |  —  | completar invariantes e compatibilidade legada |

Os testes podem reutilizar doubles em memória e mocks existentes. Nenhum teste
deve depender de credencial, serviço externo ou player de pagamento real.

## 5. Critérios de aceite

- [ ] O check `SonarCloud Code Analysis` do PR #63 informa **Coverage on New
  Code ≥ 80%**.
- [ ] A cobertura calculada localmente contra `origin/hml` confirma pelo menos
  80% da combinação de linhas e condições novas, ou a diferença é explicada
  pelos metadados da análise e confirmada no check do Sonar.
- [ ] `./gradlew --no-daemon --max-workers=1 clean test jacocoTestReport` passa
  com zero falhas e o relatório XML é gerado no caminho configurado.
- [ ] As suítes focadas de orquestração, reconciliação, aceite, ferramentas,
  modelos e persistência passam isoladamente.
- [ ] Não há alteração no threshold JaCoCo, em `sonar.exclusions`, na superfície
  de ferramentas Hermes, no `SOUL.md` ou nas integrações produtivas como parte
  desta remediação.
- [ ] Não há regressão nos testes do plugin, frontend, contratos ou isolamento.
- [ ] `git diff --check` passa e o diff contém somente a especificação/checklist
  e testes necessários, salvo justificativa técnica explícita.
- [ ] O relatório de handoff lista comandos, resultados, cobertura antes/depois,
  arquivos alterados, critérios cobertos e riscos residuais.

## 6. Edge cases e falhas a preservar

- Aceite isolado só é válido depois de termos apresentados e auditados.
- Aceite ambíguo, negativo ou antecipado não pode liberar pagamento.
- Inbound novo não pode ser respondido por um turno obsoleto.
- Comprovante recebido continua aguardando decisão humana.
- Falha de CAS, duplicidade de insert e documento legado não podem apagar
  evidência anterior.
- O branch impossível de `NoSuchAlgorithmException` não deve ser coberto com
  alteração artificial de produção; só pode permanecer não exercitado se os
  critérios globais forem atingidos e a decisão ficar documentada.

## 7. Validação e evidências

### Durante a implementação

- Executar os testes focados após cada grupo de cenários.
- Regenerar o JaCoCo com JDK 21.
- Comparar as linhas/condições novas contra a base `hml` e registrar o cálculo.

### Antes do handoff para QA

- Executar a suíte Gradle completa, incluindo `jacocoTestReport`.
- Executar plugin/profile, contratos, isolamento e frontend quando o diff exigir
  regressão nessas áreas.
- Executar `git diff --check`.

### Aceitação independente

- QA deve revisar os testes novos, executar as suítes e verificar o check do
  Sonar no PR sem aceitar a própria implementação.
- A aprovação depende do indicador do Sonar e dos testes, não apenas do número
  de testes adicionados.

## 8. Fora de escopo

- Alterar comportamento conversacional, catálogo, preços, termos, pagamento ou
  handoff.
- Alterar o `SOUL.md` ou a superfície do plugin Hermes.
- Reduzir o threshold de 80%, mudar quality gate ou adicionar exclusões para
  esconder código novo.
- Criar player/link de pagamento, transação real, nova integração ou deploy.
- Reescrever a suíte inteira ou remover testes existentes.
- Fazer merge do PR ou promover para `main`.

## 9. Dúvidas em aberto

Nenhuma decisão de negócio está pendente. Se o Sonar e o cálculo local
discordarem, o check do SonarCloud do próprio PR é a fonte de aceitação e a
diferença deve ser registrada no handoff para investigação posterior.

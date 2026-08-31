# Quickstart — validação da implementação

Os comandos abaixo foram executados em 2026-08-28. O ambiente Java padrão do
shell é Java 11; use explicitamente o JDK 21 da aplicação.

## 1. Testes determinísticos Java

```bash
cd apps/urbana-connect-api
JAVA_HOME=/Users/emanueljoseguimaraesbrito/.sdkman/candidates/java/21.0.12-tem \
  ./gradlew test --no-daemon --console=plain
```

Resultado final: `BUILD SUCCESSFUL`, 423 testes, 0 falhas e relatório JaCoCo
gerado.

Para a rodada focada:

```bash
JAVA_HOME=/Users/emanueljoseguimaraesbrito/.sdkman/candidates/java/21.0.12-tem \
  ./gradlew test --no-daemon --console=plain \
  --tests '*ReceptionOrchestratorTest' \
  --tests '*TermsAcceptanceUseCaseTest' \
  --tests '*StatefulDomainToolServiceTest' \
  --tests '*ReceptionTurnReconciliationTest' \
  --tests '*ActiveTurnLeaseServiceTest'
```

Resultado final: 58 testes focados passantes (24 de política, 28 de ferramenta
e 6 de reconciliação), incluindo as regressões de ambiente tentativo,
pagamento preparado e aceite ambíguo.

## 2. Contratos do profile/plugin

```bash
cd integrations/hermes-agent/plugins/urbana-domain
python3 -m unittest test_tools.py

cd ../..
./scripts/verify-tool-surface.sh
./scripts/smoke-contract.sh
./scripts/smoke-isolation.sh
```

Resultados: `14` testes do plugin passantes; superfície
`tool_surface=urbana-domain:6`; contrato Hermes e isolamento filesystem/profile
read-only/rede passantes. O smoke de contrato usa somente o servidor Hermes
local e não credenciais reais.

## 3. Frontend e E2E

```bash
cd apps/poc-chat
npm test -- --run
npm run typecheck
npm run lint
npm run build
```

Resultados: 19 arquivos/81 testes passantes; typecheck, lint e build Vite
passantes.

O stack local foi reconstruído com:

```bash
cd integrations/hermes-agent
./scripts/run-local.sh -d
```

Backend e frontend ficaram prontos (`READY` e `ok`). A validação live foi:

```bash
cd apps/poc-chat
PLAYWRIGHT_LIVE=1 PLAYWRIGHT_BASE_URL=http://127.0.0.1:3000 \
PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' \
npx playwright test e2e/quality-chat.spec.ts
```

Resultado: 1 teste passante, rubric de qualidade `18/18`, retomada
`HUMANO → URBA` `COMPLETED`. O Chromium bundled não é compatível com macOS 12;
foi usado o Chrome instalado no sistema.

## 4. Casos mínimos cobertos

1. `Aceito`, com case/espaços/pontuação simples, só vale após termos
   apresentados; `Ok`, negação e aceite antecipado permanecem sem aceite.
2. `Aceito, quero pagar no cartão` e o par `Aceito` + método preservam a ordem
   aceite → método → pagamento.
3. Evidência de aceite persiste serviço, unidade/ambiente, recurso, instantes,
   IDs e texto exato; ausência de evidência impede o pagamento.
4. Ambiente sem evidência textual confirmada fica `TENTATIVE`, não cria unidade
   de contratação e bloqueia `prepare_terms`.
5. Decor Reforma mantém R$ 450, até 20 m², consultoria online, Manual do Espaço,
   Tour Virtual, três opções, duas rodadas, suporte e exclusões.
6. A primeira mensagem de pagamento simulado orienta `1 serviço para cada
   ambiente` e pede comprovante; não afirma capacidade de player real.
7. Comprovante gera handoff humano sem confirmação automática; aprovação e
   mensagem humana são etapas separadas.

## 5. Validação conversacional posterior

O replay literal da Yohanna e o corpus C01–C16 ainda precisam ser executados
conforme os Gates 3–5 da spec. O relatório em
[`evidence/validation-report.md`](evidence/validation-report.md) registra o
fluxo live de 18/18, as limitações, o risco residual de publicação concorrente
e o TODO do player. A transcrição baseline integral está em
[`evidence/yohanna-baseline.md`](evidence/yohanna-baseline.md).

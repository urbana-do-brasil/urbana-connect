# Quickstart: validar o pass-through Hermes

## Pré-requisitos

- Docker Engine acessível pelo contexto local;
- `.env.poc` configurado localmente e ignorado pelo Git;
- Java 21 e Node compatíveis com os serviços;
- MongoDB, Hermes, Urbana Connect e `poc-chat` ativos.

## Validação automatizada

```bash
cd apps/urbana-connect-api && ./gradlew --no-daemon --max-workers=1 test
cd ../poc-chat && npm run test -- --run && npm run typecheck && npm run lint && npm run build
cd ../..
./integrations/hermes-agent/scripts/smoke-contract.sh
ruby quality/conversation-corpus/self-test.rb
npx playwright test
```

Os comandos live devem ser executados somente quando as credenciais locais e o
runtime estiverem disponíveis. Nunca registrar valores de `.env.poc`.

## Validação literal

1. Enviar pelo chat local uma mensagem contendo pontuação e espaços
   distintivos.
2. Capturar o `correlationId`/`eventId` retornado pelo aceite.
3. Conferir no histórico Hermes a resposta textual do agente.
4. Conferir no Mongo o outbound da mesma correlação.
5. Consultar a projeção HTTP e conferir o texto exibido no browser.
6. Comparar as três strings por igualdade exata, não por similaridade.

Também executar pelo menos três mensagens sequenciais e confirmar ausência do
prefixo repetido e do fallback antigo.

## Evidências desta execução — 2026-08-07

- `./gradlew --no-daemon --max-workers=1 --init-script /tmp/urbana-connect-exclude-duplicate.gradle --rerun-tasks test jacocoTestReport`, com Java 21: **337 testes, 0 falhas, 0 erros, 0 skips**; JaCoCo 80,94% instruções, 82,35% linhas e 60,46% branches. O init script só contorna a duplicata preexistente `PocReceptionWorker 2.java` e não altera o worktree.
- Testes focados do pass-through: parser textual, gateway, evento inbound e orquestrador verdes; TDD cobriu texto literal, resposta vazia sem outbound e falha técnica sem bubble.
- `poc-chat`: Vitest **19 arquivos/66 testes**, `typecheck`, `lint` e `build` verdes.
- Playwright com Chrome do sistema: **7 cenários determinísticos aprovados**; com `PLAYWRIGHT_LIVE=1`, os 7 determinísticos e o cenário live dos três contatos reais passaram (**8 passed**, 44,5s).
- `./integrations/hermes-agent/scripts/install-local.sh`, `./integrations/hermes-agent/scripts/smoke-contract.sh`, `./integrations/hermes-agent/scripts/smoke-isolation.sh`, `./integrations/hermes-agent/scripts/verify-tool-surface.sh` e `ruby quality/conversation-corpus/self-test.rb`: aprovados; corpus 18 execuções, 104 assertions, zero falhas.
- `HERMES_MODEL=openai/gpt-5.6-luna HERMES_LIVE_MODEL_SMOKE=1 ./integrations/hermes-agent/scripts/smoke-contract.sh`: contrato e modelo live aprovados.
- Em uma sessão live controlada, a última mensagem `assistant` do histórico Hermes, o outbound em `reception_messages`, a projeção HTTP e o conteúdo da bolha no DOM foram comparados por igualdade exata: 83 caracteres e SHA-256 `a8607341f05b37e72b556241020cd2176f7665ee046ff42fb9c901cf61272eda` em todas as camadas.
- Docker Desktop (`desktop-linux`) permaneceu acessível; Urbana Connect e `poc-chat` foram reconstruídos/recriados, readiness retornou `READY`, Mongo ficou saudável e o CSS novo foi empacotado.
- Durante a recarga do profile, o container Hermes entrou em estado sem exit
  event; o Docker Desktop foi reiniciado via CLI, sem remoção de volumes/imagens.
  Depois da recuperação, os seis serviços ficaram ativos, o hash do `SOUL.md`
  montado no Hermes coincidiu com o arquivo do repositório e os smokes de
  contrato/isolamento voltaram a passar.

### Riscos residuais

- O build sem o init script temporário continua bloqueado pela duplicata
  preexistente `PocReceptionWorker 2.java`; ela foi preservada conforme a regra
  de não descartar alterações existentes.
- Sessões antigas persistidas com o alias inválido `hermes-agent` podem falhar
  no OpenRouter. O gateway atual envia o modelo configurado na criação de novas
  sessões; nenhuma sessão antiga foi removida durante a validação.

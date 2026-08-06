# Corpus sintético Hermes

Os arquivos em `scenarios/` são fixtures locais, não representam clientes ou
cobranças reais e devem permanecer independentes do webhook produtivo. O
formato é validado pelo schema versionado em `schema/scenario.schema.json` e
por validações semânticas de allowlist/existência de fixtures. O validator é
próprio e usa somente a biblioteca padrão do Ruby; não requer a gem
`json-schema`.

## Execução

Com MongoDB e a aplicação POC disponíveis:

```bash
./corpus/run-local.sh --repetitions 3
```

Antes de chamar a API, o script valida todos os cenários. Para validar um
arquivo isolado:

```bash
ruby ./corpus/report.rb validate --scenario ./corpus/scenarios/01-happy-first-contact.yml
```

É possível apontar para outro ingresso local:

```bash
./corpus/run-local.sh --base-url http://127.0.0.1:8081 --repetitions 3
```

O runner executa cada cenário e grava artefatos ignorados pelo Git em
`corpus/results/`. Cada execução recebe `runId`, `repetition`, `contactId`,
`eventId` e chaves de isolamento derivadas para a sessão; os IDs lógicos das
assertivas permanecem estáveis no relatório. A API POC não expõe o
`hermesSessionId`, então o relatório registra essa limitação explicitamente em
`sessionIdVerified=false`, sem declarar a sessão observada.

## Assertivas e avaliação

Cada assertiva gera um item em `assertions` com `expected`, `observed`,
`passed` e `status` (`PASSED`, `FAILED` ou `UNVERIFIED`). Quando a API não
oferece evidência suficiente — por exemplo, agrupamento temporal, chamadas
internas do Hermes ou ledger de ferramentas — o item fica `UNVERIFIED` e a
execução reprova. O campo legado `manualAssertions` é apenas um recorte desses
itens; ele não afrouxa o gate.

### Memória e sentinel

`memory` e `isolationSentinel` são consumidos quando declarados. A API POC
atual não oferece endpoint de seed. No modo padrão `verify-only`, os fatos
declarados são registrados como não verificáveis e a execução reprova. O modo
explícito `setup-events` só executa `memory.setupEvents` declarados no próprio
cenário pela mesma API de mensagens e só passa após conferir os fatos na
projeção:

```bash
./corpus/run-local.sh --memory-seed-mode setup-events
```

Não há fallback que converta metadados de memória em fatos nem que fabrique
sucesso.

Após as três repetições, revise `naturalness`, `clarity` e `usefulness` numa
escala de 1 a 5 no relatório. A média mínima da POC é 4, e os bloqueios
comerciais e vazamentos de memória não admitem exceção.

O runner nunca imprime variáveis de segredo nem faz `source` de `.env.poc`. O
`run-local.sh` lê somente o valor de `HERMES_INTERNAL_TOOL_TOKEN` para enviar o
Bearer da API sintética (ou aceita `POC_API_TOKEN` já exportado); a API POC
local usa somente os endpoints simulados sem envio real ao WhatsApp.

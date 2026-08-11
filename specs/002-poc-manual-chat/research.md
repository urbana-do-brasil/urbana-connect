# Research: Chat local para testes manuais da Urba

## Decision 1 — React SPA com TypeScript e Vite

**Decision**: usar React 19.2, TypeScript 5.x e Vite 8 para uma SPA sem SSR,
React Server Components, roteador ou biblioteca global de estado.

**Rationale**: a aplicação possui uma única tela e estado local moderado. React
atende a decisão de tecnologia feita com Emanuel, TypeScript torna os contratos
da projeção explícitos e Vite produz um build estático simples. A ausência de SSR
mantém o runtime sem Node e evita componentes de servidor que não agregam valor
ao simulador.

**Alternatives considered**:

- Vue: tecnicamente adequado, mas sem vantagem específica ou convenção prévia no
  repositório.
- Angular: adicionaria estrutura, runtime de desenvolvimento e conceitos além do
  necessário para uma única tela.
- HTML/JavaScript sem framework: minimizaria dependências, mas aumentaria o custo
  de coordenar estado concorrente e testar múltiplas conversas.

**Primary sources**:

- [React 19.2 release information](https://react.dev/blog/2025/10/01/react-19-2)
- [Vite 8 announcement and Node requirements](https://vite.dev/blog/announcing-vite8)

## Decision 2 — Node.js 24 LTS somente no build e nos testes

**Decision**: fixar Node.js 24 LTS no estágio de build e nos ambientes de teste;
o container final contém apenas arquivos estáticos e Nginx.

**Rationale**: em agosto de 2026, Node 24 está em LTS enquanto Node 26 ainda está
na linha Current. O projeto deve preferir LTS e não precisa manter um processo
Node em execução.

**Alternatives considered**:

- Node 26 Current: mais recente, porém ainda não LTS na data do planejamento.
- Node 22 LTS: compatível, mas com janela de suporte menor para uma feature nova.
- Node no host e build exclusivamente remoto: dificulta execução rápida dos
  testes durante desenvolvimento.

**Primary source**:

- [Node.js release status](https://nodejs.org/en/about/previous-releases)

## Decision 3 — Nginx sem privilégios como servidor estático e proxy restrito

**Decision**: usar uma imagem Nginx unprivileged pinada por versão e digest para
servir a SPA e encaminhar somente as rotas de chat necessárias.

**Rationale**: um único processo atende arquivos estáticos, same-origin e
injeção server-side do token. O runtime pode operar com filesystem read-only,
porta 8080 sem root, capabilities removidas e rede mínima.

**Alternatives considered**:

- Servir o frontend dentro do Spring Boot: acopla a ferramenta descartável ao jar
  e aumenta o risco de inclusão em profiles não POC.
- Servidor Node/Express: facilitaria transformação de respostas, mas adicionaria
  runtime e código server-side sem necessidade no MVP.
- Frontend chamando Urbana Connect diretamente: exigiria expor o bearer token ao
  navegador ou relaxar a autenticação da POC.

**Primary sources**:

- [Official NGINX Dockerfiles](https://github.com/nginx/docker-nginx)
- [NGINX unprivileged image](https://github.com/nginx/docker-nginx-unprivileged)

## Decision 4 — Proxy allowlist em vez de liberar toda a API POC

**Decision**: expor pelo host do chat somente `POST .../messages`,
`GET .../{contactAlias}` e o healthcheck local. O proxy deve substituir qualquer
header `Authorization` recebido e negar `flush`, métricas, aprovação de pagamento
e ferramentas internas.

**Rationale**: o navegador precisa apenas enviar texto e consultar histórico. A
allowlist reduz o poder da interface, preserva o escopo de cliente e mantém a
credencial fora do bundle, storage e tráfego originado no JavaScript.

**Alternatives considered**:

- Reutilizar diretamente todas as rotas POC: funcional, porém expõe operações que
  contradizem a interface exclusivamente de cliente.
- Remover autenticação quando o profile POC estiver ativo: simplifica chamadas,
  mas amplia desnecessariamente a superfície do backend publicado no host.
- Criar login local: não agrega proteção proporcional para ferramenta loopback e
  conflita com o MVP acordado.

## Decision 5 — Consulta periódica da projeção enquanto houver turnos pendentes

**Decision**: consultar a projeção a cada 1 segundo somente para contatos com
entrada pendente; buscar uma vez ao abrir contatos ociosos. Aplicar backoff em
falhas transitórias e interromper ao observar saída, handoff ou timeout de UI.

**Rationale**: o backend já oferece projeção canônica, scheduler e serialização
por contato. Polling limitado aos turnos ativos satisfaz o objetivo de até 2
segundos sem criar um novo protocolo de streaming nem alterar o core validado.

**Alternatives considered**:

- Server-Sent Events: eficiente para atualizações, porém requer novo endpoint e
  ciclo de conexão no backend.
- WebSocket: complexidade desproporcional e estado de conexão bidirecional sem
  necessidade de negócio.
- Chamar `flush` e usar sua resposta: destruiria o comportamento real de
  mensagens fragmentadas confirmado na entrevista.

## Decision 6 — Reconciliação por identificadores canônicos

**Decision**: usar `eventId` para entradas, `correlationId` para relacionar o
turno e o identificador persistido da mensagem para deduplicar a projeção. O
estado otimista é substituído pelo item canônico quando ele aparecer.

**Rationale**: tempo e texto não são chaves seguras; mensagens iguais podem ser
enviadas no mesmo instante. Os IDs existentes permitem retries e consultas
repetidas sem duplicação visual.

**Alternatives considered**:

- Deduplicar por texto + horário: produz falso positivo em mensagens repetidas.
- Manter transcript próprio no browser: cria uma segunda fonte de verdade e pode
  divergir do MongoDB.
- Gerar novo evento a cada retry: viola idempotência e pode gerar dois turnos.

## Decision 7 — Estado local versionado e estritamente visual

**Decision**: usar uma única chave `urbana.poc-chat.v1` no `localStorage` contendo
somente aliases, nomes amigáveis, timestamps de UI, arquivamento, cursor de leitura
e contato ativo. Não persistir mensagens, payloads pendentes, fatos ou segredos.

**Rationale**: a lista precisa sobreviver ao reload, enquanto o transcript deve
continuar canônico no backend. Um schema versionado permite descartar ou migrar
estado inválido sem afetar a conversa real.

**Alternatives considered**:

- IndexedDB: capacidade e complexidade desnecessárias para poucos metadados.
- Nova coleção MongoDB para contatos da UI: mistura preferência local descartável
  com estado de domínio e exige endpoints administrativos.
- Apenas memória: perderia contatos a cada reload e impediria o cenário recorrente.

## Decision 8 — Testes orientados ao comportamento do usuário

**Decision**: Vitest e React Testing Library cobrem unidades e componentes;
Playwright cobre os fluxos de navegador. O smoke com Hermes real permanece
separado dos testes determinísticos.

**Rationale**: a Testing Library incentiva consultas e interações semelhantes às
de uma pessoa usuária. Playwright valida browser, assets, proxy e concorrência; o
smoke real confirma integração sem tornar toda a suíte dependente de LLM e rede.

**Alternatives considered**:

- Somente testes unitários: não cobrem Nginx, Compose ou comportamento real do
  navegador.
- Somente E2E live: lentos, custosos e não determinísticos.
- Cypress: adequado, mas Playwright oferece ambiente oficial de browser e já
  documenta execução local, headed e em Docker com pin de versão.

**Primary sources**:

- [Testing Library guiding principles](https://testing-library.com/docs/)
- [Playwright installation](https://playwright.dev/docs/intro)
- [Playwright Docker guidance](https://playwright.dev/docs/docker)

## Decision 9 — Identidade visual Urbana, sem cópia de marca do WhatsApp

**Decision**: usar padrões reconhecíveis de chat — lista, balões, cabeçalho,
composer, horários e não lidos — com cores e identidade Urbana e a indicação
"Simulador local".

**Rationale**: reproduz a experiência relevante para o teste sem criar confusão
com um produto oficial da Meta nem assumir o custo de fidelidade pixel a pixel.

**Alternatives considered**:

- Clone visual exato: trabalho sem ganho para a validação conversacional.
- Interface técnica genérica: contraria a intenção de experimentar a conversa
  como cliente.

## Decision 10 — Nenhuma alteração no webhook ou no caminho produtivo

**Decision**: integrar exclusivamente ao `ConversationSimulatorController` já
condicionado pelo profile POC. Não modificar `WebhookController`,
`ConversationFlowService`, `WhatsAppCloudApiGateway` ou manifests de produção.

**Rationale**: a ferramenta existe para validar manualmente a POC isolada. Manter
o canal real intacto preserva a reversibilidade e evita inferir que o smoke local
valida entrega pela Meta.

**Alternatives considered**:

- Enviar payload de WhatsApp ao webhook: exercitaria o fluxo legado, não o núcleo
  Hermes-first desta POC.
- Mockar WhatsApp Cloud API: amplia escopo e ainda não valida a infraestrutura
  real da Meta.

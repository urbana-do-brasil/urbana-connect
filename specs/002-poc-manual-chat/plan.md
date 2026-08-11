# Implementation Plan: Chat local para testes manuais da Urba

**Branch**: `002-poc-manual-chat` | **Date**: 2026-08-06 | **Spec**: [spec.md](./spec.md)
**Consolidated validation branch**: `feat/pee-101`
**Input**: Feature specification from `/specs/002-poc-manual-chat/spec.md`

## Summary

Adicionar uma aplicação web local e independente para uma pessoa testadora
conversar por texto com a Urba através do ingresso sintético Hermes-first já
existente. A aplicação oferecerá múltiplos contatos, retomada de histórico,
processamento concorrente e acompanhamento assíncrono sem acionar `flush`.

O frontend será uma SPA estática React/TypeScript construída em imagem
multi-stage e servida por um Nginx sem privilégios. O mesmo Nginx atuará como
proxy de escopo mínimo para a API sintética: o navegador acessará somente os
dois contratos necessários, enquanto o token da POC permanecerá no container.
O serviço fará parte exclusivamente do Compose local Hermes-first e ficará
publicado em `127.0.0.1`.

## Technical Context

**Language/Version**: TypeScript 5.x e React 19.2; Node.js 24 LTS somente para build/test; Java 21 LTS permanece inalterado no backend
**Primary Dependencies**: React 19.2, Vite 8, Nginx unprivileged; Vitest, React Testing Library e Playwright para testes
**Storage**: MongoDB existente como fonte canônica; `localStorage` versionado somente para metadados dos contatos e estado visual
**Testing**: Vitest + React Testing Library para unidade/integração de UI; Playwright para navegador; JUnit/Gradle e scripts Hermes para regressão
**Target Platform**: navegador desktop moderno e Docker Compose local em macOS/Linux; artefato estático servido em container
**Project Type**: aplicação web frontend separada integrada a um backend existente
**Performance Goals**: resposta já persistida visível em até 2 s; interação visual sem bloqueio durante processamento; build estático reproduzível
**Constraints**: somente texto; sem `flush`; sem credencial no navegador; bind em `127.0.0.1`; nenhuma configuração de produção; nomes amigáveis nunca enviados ao backend
**Scale/Scope**: uma pessoa testadora local, até 50 contatos locais e históricos de até 1.000 mensagens por contato no MVP

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

### Pre-design

| Principle | Status | Evidence |
| --- | --- | --- |
| I. Stack oficial e coerência | PASS | A adoção isolada de TypeScript/React foi decidida explicitamente com Emanuel, não altera o baseline Java/Spring e fica documentada em `research.md`. |
| II. Clean Architecture | PASS | O frontend contém apenas estado e apresentação; regras comerciais continuam no domínio Java e integrações permanecem nas bordas. |
| III. Specification-first e test-first | PASS | `spec.md` e checklist foram concluídos antes do plano; cada comportamento de UI terá teste anterior à implementação. |
| IV. Qualidade automatizada | PASS | O plano preserva o gate Java existente e adiciona cobertura e testes de navegador para o novo projeto web. |
| V. Homolog primeiro | PASS | O código seguirá o fluxo normal de PR, mas o serviço é deliberadamente local e não será incluído em manifests de homologação ou produção. |

### Post-design

Todos os gates permanecem **PASS** após o desenho. O proxy não recebe regra de
negócio, o novo armazenamento local exclui transcripts e segredos, e nenhuma
dependência do frontend entra no runtime Java.

## Architecture

```text
Browser em 127.0.0.1
        │
        │ HTML/CSS/JS e /api/poc/conversations/*
        ▼
poc-chat (Nginx sem privilégios)
        │  adiciona token apenas no proxy
        │  permite somente POST messages e GET projection
        ▼
Urbana Connect / ingresso sintético
        │
        ├── janela real de agrupamento e scheduler
        ├── MongoDB (transcript canônico)
        └── Hermes Sessions API
                │
                ▼
          resposta persistida
                │
                └── GET projection → Nginx → Browser
```

### Boundaries

- `poc-chat` controla somente apresentação, metadados visuais e coordenação de
  requisições.
- Urbana Connect continua responsável por identidade canônica do contato,
  agrupamento, idempotência, transcript, memória e regras comerciais.
- O Nginx serve estáticos e aplica a fronteira de rede/autenticação. Ele não
  transforma conteúdo conversacional nem decide estado de negócio.
- O navegador não chama Hermes, MongoDB, webhook real, métricas, `flush`,
  aprovação de pagamento ou ferramentas internas.

## Key Technical Decisions

### 1. SPA mínima sem framework adicional de estado

Usar React com reducer/contexto local, `fetch` e CSS próprio. Não adicionar
roteador, biblioteca global de estado, design system ou biblioteca de componentes
no MVP. A aplicação possui uma única tela e não justifica essas dependências.

### 2. Proxy same-origin como guardião do token

O bundle chama caminhos relativos no próprio host. O Nginx substitui qualquer
`Authorization` recebido e injeta o `HERMES_POC_API_TOKEN` exclusivamente ao
encaminhar rotas allowlisted. A configuração é materializada no startup do
container a partir de template; o arquivo resultante e os diretórios temporários
ficam em `tmpfs`.

O proxy deve permitir apenas:

- `POST /api/poc/conversations/{contactAlias}/messages`;
- `GET /api/poc/conversations/{contactAlias}`;
- `GET /health` do próprio frontend.

Qualquer rota de métricas, `flush`, pagamento ou ferramenta interna retorna
`404` ou `405` no frontend.

### 3. Acompanhamento assíncrono sem alterar o backend

Após um `202 QUEUED`, o cliente consulta a projeção do contato a cada 1 segundo
enquanto houver entrada sem resposta correspondente. Contatos inativos são
consultados apenas ao abrir/recarregar; contatos pendentes continuam sendo
acompanhados em segundo plano.

O frontend reconcilia mensagens por `eventId`, `correlationId` e direção. Uma
resposta persistida deverá aparecer em no máximo um ciclo adicional de consulta.
Não serão introduzidos WebSocket, SSE ou novo endpoint nesta fase.

### 4. Idempotência de envio e retry

Cada envio gera no navegador um `eventId` estável no formato
`ui-<crypto.randomUUID()>`. Em falha de transporte incerta, a mesma requisição é
repetida uma vez com o mesmo identificador. `202` e `409` significam que o
acompanhamento da projeção pode continuar; nunca se gera novo `eventId` para uma
retentativa da mesma mensagem.

O payload pendente existe somente em memória. Após reload, uma entrada persistida
sem saída correspondente pode ser reconstruída da projeção para uma retentativa
segura, sem gravar o texto no armazenamento local.

### 5. Identidade visual separada da identidade conversacional

`displayName` existe somente no estado local versionado. O alias enviado ao
backend é `manual-<UUID>`, compatível com o contrato atual e sem relação com o
nome exibido. Testes inspecionarão cada requisição para impedir vazamento do nome
amigável em URL, headers ou corpo.

### 6. Persistência mínima no navegador

Persistir somente versão do schema, contatos, aliases, timestamps visuais,
estado arquivado, cursor de leitura e contato ativo. Mensagens, fatos,
correlações, tokens e respostas não entram no `localStorage`; os transcripts são
sempre reconstruídos da projeção canônica.

### 7. Respeito ao comportamento real de fragmentação

O frontend não chama `/flush`. Cada Enter envia um evento textual independente;
fragmentos enviados dentro da janela móvel existente são agrupados pelo backend.
O estado "Urba está processando" permanece até uma saída canônica, handoff ou
falha recuperável observável.

### 8. Isolamento operacional

O serviço `poc-chat` será adicionado somente a
`infra/local-poc/docker-compose.poc.yml`, conectado apenas à rede `poc_ingress` e exposto
como `127.0.0.1:${POC_CHAT_HOST_PORT:-3000}:8080`. O container terá filesystem
somente leitura, usuário sem privilégios, capabilities removidas e
`no-new-privileges`.

## Project Structure

### Documentation (this feature)

```text
specs/002-poc-manual-chat/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── checklists/
│   └── requirements.md
└── contracts/
    ├── browser-storage.schema.json
    └── chat-api.yaml
```

### Source Code (repository root)

```text
apps/poc-chat/
├── src/
│   ├── api/
│   │   ├── conversationClient.ts
│   │   └── contracts.ts
│   ├── components/
│   │   ├── ConversationList.tsx
│   │   ├── ChatView.tsx
│   │   ├── MessageBubble.tsx
│   │   └── MessageComposer.tsx
│   ├── state/
│   │   ├── contactStore.ts
│   │   ├── conversationReducer.ts
│   │   └── conversationTracker.ts
│   ├── test/
│   │   └── setup.ts
│   ├── App.tsx
│   ├── main.tsx
│   └── styles.css
├── e2e/
│   └── manual-chat.spec.ts
├── nginx/
│   ├── nginx.conf
│   └── default.conf.template
├── Dockerfile
├── package.json
├── package-lock.json
├── playwright.config.ts
├── tsconfig.json
└── vite.config.ts

hermes/
└── docker-compose.poc.yml
```

**Structure Decision**: manter o chat como aplicação separada em `apps/poc-chat/`.
Nenhum arquivo frontend entra em `apps/urbana-connect-api/src/main/resources`, evitando acoplamento
ao jar ou inclusão acidental em outros profiles.

## Implementation Phases

### Phase 0 — Contracts and failing tests

1. Fixar `package-lock.json`, scripts de build/test/coverage e configuração
   TypeScript estrita.
2. Escrever testes falhando para armazenamento versionado, alias opaco e não
   persistência de transcripts.
3. Escrever testes falhando para cliente HTTP, `202`, `409`, falha incerta e
   reconciliação de projeções.
4. Escrever testes falhando para múltiplos contatos, não lidos, troca durante
   processamento e reload.
5. Escrever teste de contrato que prova que `displayName` não aparece em nenhuma
   requisição.

### Phase 1 — Minimal client and state

1. Implementar modelos e validações do contrato.
2. Implementar store local versionado com migração segura para estado vazio.
3. Implementar cliente de envio e leitura da projeção.
4. Implementar coordenador de acompanhamento por contato, deduplicação e retry.

### Phase 2 — User interface

1. Implementar lista de contatos, criação, seleção, arquivamento e não lidos.
2. Implementar histórico, balões, horários, links seguros e scroll.
3. Implementar composer com Enter para envio e Shift+Enter para quebra de linha.
4. Implementar estados de processamento, indisponibilidade e retentativa.
5. Aplicar identidade visual da Urbana e rótulo "Simulador local".

### Phase 3 — Container and Compose

1. Criar build multi-stage e runtime Nginx sem privilégios.
2. Configurar proxy allowlist e injeção server-side do token.
3. Adicionar `poc-chat` ao Compose local com bind loopback e hardening.
4. Validar `nginx -t`, healthcheck, ausência de CORS e bloqueio das rotas fora do
   escopo.

### Phase 4 — Independent acceptance

1. Executar unidade, integração, cobertura e build do frontend.
2. Executar Playwright para as cinco histórias da spec.
3. Executar smoke live com pelo menos três contatos e uma conversa real Hermes.
4. Reexecutar `./gradlew check`, testes do plugin Python e scripts Hermes
   relevantes para regressão.
5. Inspecionar bundle, armazenamento e tráfego do navegador para comprovar que o
   token e os nomes amigáveis não vazaram.

## Test Strategy

| Layer | Evidence required |
| --- | --- |
| Pure state | Vitest cobre contatos, aliases, persistência permitida, arquivamento, não lidos e transições. |
| UI behavior | React Testing Library cobre criação, envio, teclado, troca de conversa, loading, erro e retry pelo ponto de vista do usuário. |
| HTTP contract | Testes com `fetch` controlado cobrem payload, `202`, `409`, erro, timeout e projeção inválida. |
| Browser | Playwright cobre US1–US5 em Chromium, com rotas determinísticas e um smoke separado contra o stack real. |
| Container | Build, `nginx -t`, healthcheck, headers, allowlist, bind local e filesystem read-only. |
| Regression | Gradle `check`, plugin Hermes Python, smoke de contrato, isolamento e tool surface. |

O frontend deve manter no mínimo 80% de linhas, statements e funções e 70% de
branches. Esses percentuais complementam, sem substituir, o gate JaCoCo existente.

## Risks and Mitigations

| Risk | Mitigation |
| --- | --- |
| Token parar no bundle ou DevTools | Proxy same-origin injeta o header; teste varre build, storage e requisições do navegador. |
| Nome visual contaminar o Hermes | Alias opaco e teste negativo em URL, headers e payload. |
| Polling duplicar respostas | Reconciliação por IDs canônicos e atualização imutável por contato. |
| Reload perder mensagem ainda apenas no batch em memória | Após reload, buscar projeção e acompanhar contatos cujo último turno não tenha saída; a janela do backend continua independente do browser. |
| Handoff parecer timeout | Usar `conversation.mode` internamente para encerrar o indicador e preservar o silêncio automático esperado. |
| Compose expor chat na rede local | Bind explícito em `127.0.0.1`, rede mínima e smoke negativo fora do host. |
| Frontend virar canal paralelo | Nenhum webhook, Meta SDK ou manifest de produção; documentação o classifica como ferramenta descartável da POC. |

## Complexity Tracking

Não há violação constitucional. O segundo projeto é justificado pela decisão
explícita de criar uma aplicação web separada e evita misturar toolchain frontend
ao runtime Java. Nenhum serviço de domínio ou armazenamento novo é introduzido.

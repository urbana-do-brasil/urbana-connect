# Research: Núcleo conversacional Hermes-first

## Sessions API nativa

**Decision**: fixar Hermes Agent `v2026.8.3` e usar `POST /api/sessions`, `POST /api/sessions/{id}/chat` e `GET /api/sessions/{id}/messages`.

**Rationale**: sessões são entidades persistentes, inspecionáveis e reutilizáveis; isso materializa a decisão de deixar o Hermes manter contexto sem a Urbana reconstruí-lo em cada turno.

**Alternatives considered**:

- `/v1/chat/completions`: stateless e exige reenviar histórico.
- `/v1/responses`: aceita encadeamento, mas o armazenamento de responses é limitado a 100 itens por LRU e é uma superfície de compatibilidade.

**Source**: [Hermes API Server](https://hermes-agent.nousresearch.com/docs/user-guide/features/api-server/)

### Verified implementation nuances

- A versão pinada executa o agente com `task_id=session_id`; isso identifica a sessão, não um turno distinto.
- A resposta síncrona contém `message.content` livre e não oferece garantia de `response_format` na Sessions API.
- Compressão pode retornar um `session_id` efetivo diferente do solicitado.

Por isso a Urbana Connect usa lease ativo por sessão, validação pós-agente e atualização atômica do vínculo após rotação.

**Source**: [Hermes API server source](https://github.com/NousResearch/hermes-agent/blob/v2026.8.3/gateway/platforms/api_server.py)

## Modelo e provedor

**Decision**: OpenRouter com `openai/gpt-5.6-luna` e `reasoning_effort=max` na primeira bateria.

**Rationale**: o modelo oferece janela ampla, visão, function calling e saída estruturada. OpenRouter permite trocar o modelo sem alterar a integração.

**Alternatives considered**:

- Gemini direto: coerente com a aplicação atual, mas exigiria outra credencial local e reduziria a flexibilidade do experimento.
- OAuth pessoal do Codex: útil para ensaio técnico, mas inadequado como dependência operacional do produto.
- Luna `max` permanente: ainda não decidido; a POC mede custo e latência antes de selecionar o esforço de produção.

**Sources**: [OpenRouter GPT-5.6 Luna](https://openrouter.ai/openai/gpt-5.6-luna/api), [Hermes reasoning effort](https://hermes-agent.nousresearch.com/docs/user-guide/configuration/#reasoning-effort)

## Extensão por ferramentas restritas

**Decision**: plugin de projeto do Hermes com toolset único `urbana-domain`, encaminhando operações a uma API interna autenticada.

**Rationale**: plugins são a extensão oficial para ferramentas específicas de projeto. O plugin pode expor somente a superfície aprovada e manter o agente sem terminal, filesystem, navegador ou mensageria.

**Alternatives considered**:

- MCP remoto na Urbana Connect: arquitetura futura válida, mas a primeira versão adicionaria Spring AI/MCP e uma matriz nova de compatibilidade ao Spring Boot 3.4.
- Ferramentas nativas no fork do Hermes: aumenta manutenção e acoplamento com upstream.
- Operações codificadas apenas na saída final: impede consultas durante o raciocínio e reduz auditabilidade de cada ferramenta.

**Sources**: [Hermes Plugins](https://hermes-agent.nousresearch.com/docs/user-guide/features/plugins), [Hermes Toolsets](https://hermes-agent.nousresearch.com/docs/reference/toolsets-reference), [Hermes MCP](https://hermes-agent.nousresearch.com/docs/user-guide/features/mcp)

## Identidade e memória

**Decision**: um `contactId` opaco por contato, uma sessão persistente por `contactId`, memória global do perfil desabilitada e fatos duráveis mantidos na Urbana Connect.

**Rationale**: a sessão já preserva histórico e contexto. Desabilitar memória global elimina a principal rota de vazamento entre clientes, enquanto os fatos tipados continuam visíveis, versionados e recuperáveis.

**Alternatives considered**:

- Chave pelo número bruto: expõe dado pessoal desnecessariamente ao runtime.
- Perfil Hermes por cliente: isolamento forte, mas custo operacional e configuração inviáveis para a POC.
- Memória global do Hermes: pode misturar observações de contatos distintos.

**Sources**: [Hermes Sessions](https://hermes-agent.nousresearch.com/docs/user-guide/sessions/), [Hermes Profiles](https://hermes-agent.nousresearch.com/docs/user-guide/profiles/)

## Persona e prompt

**Decision**: perfil Hermes exclusivo com `SOUL.md` para identidade/voz e instruções operacionais estáveis no contexto do perfil.

**Rationale**: `SOUL.md` é o mecanismo oficial de identidade do agente e é isolado por perfil. Uma instância dedicada evita carregar o contexto de engenharia do repositório no agente de atendimento.

**Alternative considered**: reenviar um system prompt em cada turno, o que aumenta tokens e torna a identidade menos previsível.

**Source**: [Hermes SOUL.md](https://hermes-agent.nousresearch.com/docs/guides/use-soul-with-hermes)

## Multimodalidade

**Decision**: imagens seguem inline pela Sessions API; áudio entra como mídia persistida mais transcrição por um adapter local Whisper substituível; comprovante nunca confirma pagamento.

**Rationale**: a Sessions API documenta imagens inline. O modelo selecionado recebe texto e imagem, mas áudio requer uma etapa de transcrição separada da decisão comercial.

**Alternative considered**: deixar o Hermes conectado diretamente ao WhatsApp para usar seus adapters multimodais, rejeitado porque duplicaria a autoridade do canal.

**Source**: [Hermes API Server](https://hermes-agent.nousresearch.com/docs/user-guide/features/api-server/)

## Roteamento progressivo

**Decision**: o endpoint sintético usa o novo bounded context; o webhook atual não muda nesta POC.

**Rationale**: permite validar o coração conversacional, contratos, memória e segurança antes de afetar tráfego do canal.

**Alternative considered**: substituir imediatamente o fluxo existente, rejeitado pelo risco de regressão e pela ausência inicial de JDK 21, Docker e credencial local.

# Spec SDD — PEE-100: POC Urbana Connect + OpenClaw Sidecar

## Metadados

- `Título da feature`: POC Urbana Connect + OpenClaw Sidecar
- `Ticket Jira`: PEE-100
- `Status`: Draft
- `Responsável pela spec`: Visão Codex
- `Branch`: `feature/PEE-100-openclaw-poc-spec`
- `Contexto de branch`: `feature/* -> hml -> main`
- `Data`: 2026-05-03
- `Fonte principal`: [Refinamento - POC arquitetura conversacional inspirada no OpenClaw](https://urbanadobrasil.atlassian.net/wiki/spaces/pro/pages/376111105)

---

## 1. Contexto

A PEE-99 evoluiu a participação da IA na conversa da Urba, mas a validação em
homologação mostrou que a experiência ainda ficou abaixo do esperado. O fluxo
passou a soar mais humano, mas continuou com baixa inteligência conversacional:

- dificuldade de manter contexto simples já informado pelo usuário;
- repetição de perguntas;
- resposta ruim a perguntas diretas como "quais serviços vocês oferecem?";
- dependência excessiva do harness/state machine atual;
- sensação de que a IA executa um roteiro com pouca capacidade de conversa.

O refinamento da PEE-100 propõe validar uma hipótese diferente: usar o OpenClaw
como runtime conversacional sidecar, mantendo a Urbana Connect como aplicação
central, dona do webhook, envio WhatsApp, persistência, logs e validações
operacionais.

Esta spec descreve uma POC enxuta. O objetivo não é implementar a arquitetura
final completa, e sim provar o fluxo mínimo:

```text
WhatsApp -> Urbana Connect -> OpenClaw -> Urbana Connect -> WhatsApp
```

Se esse loop básico não melhorar a qualidade conversacional, não vale ampliar
escopo para catálogo, playbooks, interface humana ou action schema completo.

---

## 2. Princípios da POC

1. **Urbana Connect continua sendo a aplicação central.**
   A POC não deve transformar cada responsabilidade arquitetural em um
   microsserviço próprio.

2. **OpenClaw atua como sidecar conversacional.**
   Ele interpreta o turno e gera uma resposta proposta. Ele não deve enviar
   WhatsApp diretamente.

3. **O primeiro entregável é um spike técnico.**
   Antes de alterar o webhook real, a implementação deve provar que a Urbana
   Connect consegue chamar o OpenClaw com `sessionKey` e receber uma resposta.

4. **Toda integração deve ficar atrás de feature flag.**
   A POC precisa ser desligável sem deploy corretivo.

5. **Falha no OpenClaw não pode derrubar o webhook.**
   Timeout, erro de conexão ou resposta inválida devem cair em fallback
   controlado.

6. **A arquitetura deve ser revisável por PRs pequenos.**
   O contrato, o cliente OpenClaw, o webhook e a observabilidade devem poder ser
   avaliados separadamente.

---

## 3. Escopo

### Dentro do escopo inicial

- Receber mensagens diretas de texto do WhatsApp no webhook da Urbana Connect.
- Resolver uma `sessionKey` estável por conversa/usuário.
- Delegar o turno para o OpenClaw.
- Receber uma resposta textual do OpenClaw.
- Aplicar validação mínima na resposta.
- Enviar a resposta pelo fluxo atual de WhatsApp da Urbana Connect.
- Registrar logs mínimos para depuração e comparação em homologação.
- Manter o fluxo protegido por feature flag.

### Fora do escopo inicial

- Interface gráfica de atendimento.
- Handoff humano sofisticado.
- Catálogo completo de serviços como contexto dinâmico do OpenClaw.
- Playbooks/persona editáveis por UI.
- Action schema estruturado completo.
- Processamento de áudio, imagem, documento ou mídia.
- Grupos de WhatsApp.
- Permitir que o OpenClaw envie mensagens diretamente ao WhatsApp.
- Permitir tools perigosas no OpenClaw, como shell, browser, filesystem amplo,
  cron ou escrita externa.
- Substituir a state machine atual.
- Reescrever o fluxo comercial inteiro.

---

## 4. Desenho técnico proposto

### 4.1 Componentes lógicos

Mesmo que o refinamento arquitetural use blocos separados, nesta POC eles devem
ser tratados como módulos/responsabilidades dentro da Urbana Connect, não como
aplicações independentes.

```text
Urbana Connect
  - WhatsApp Webhook
  - OpenClawClient
  - SessionKeyResolver
  - ResponseValidator mínimo
  - WhatsApp Message Sender
  - Logs/telemetria

OpenClaw Sidecar
  - Gateway
  - agente/workspace dedicado da Urba
  - sessão por sessionKey
```

### 4.2 Fluxo nominal

1. WhatsApp envia mensagem inbound para a Urbana Connect.
2. Urbana Connect valida que a mensagem é elegível para a POC.
3. Urbana Connect salva inbound se esse comportamento já existir no fluxo atual.
4. Urbana Connect calcula `sessionKey`.
5. Urbana Connect chama OpenClaw usando `OpenClawClient`.
6. OpenClaw processa o turno e retorna texto.
7. Urbana Connect valida minimamente a resposta.
8. Urbana Connect envia a resposta pelo WhatsApp.
9. Urbana Connect salva outbound se esse comportamento já existir no fluxo atual.
10. Urbana Connect registra logs de correlação, latência e status.

### 4.3 Estratégia de integração com OpenClaw

A primeira tentativa deve ser chamada direta da Urbana Connect para o OpenClaw
Gateway, sem criar uma aplicação intermediária.

Se a integração direta com o Gateway/WebSocket ficar mais complexa do que o
benefício esperado para a POC, pode ser criado um bridge mínimo em Node usando
o SDK do OpenClaw. Esse bridge deve ser tratado como fallback de simplicidade,
não como premissa arquitetural.

Contrato mínimo caso um bridge seja necessário:

```text
POST /conversation-turn

request:
  sessionKey: string
  text: string
  from: string
  conversationId?: string
  timestamp?: string

response:
  text: string
  status: ok | error | timeout
  errorReason?: string
```

Decisão pendente do spike:

- `A`: Urbana Connect chama OpenClaw Gateway diretamente.
- `B`: Urbana Connect chama um OpenClaw Bridge HTTP mínimo.

Nenhuma implementação de webhook real deve avançar antes dessa decisão.

---

## 5. Comportamentos esperados

1. Dado que a feature flag da POC esteja desligada, quando uma mensagem chegar
   no webhook, então a Urbana Connect deve manter o comportamento atual sem
   chamar o OpenClaw.

2. Dado que a feature flag esteja ligada e a mensagem seja uma DM textual
   suportada, quando a mensagem chegar no webhook, então a Urbana Connect deve
   delegar o turno para o OpenClaw com uma `sessionKey` estável.

3. Dado que duas mensagens venham do mesmo usuário/conversa, quando forem
   delegadas para o OpenClaw, então devem usar a mesma `sessionKey` para manter
   continuidade conversacional.

4. Dado que mensagens venham de usuários/conversas diferentes, quando forem
   delegadas para o OpenClaw, então não podem compartilhar a mesma `sessionKey`.

5. Dado que o OpenClaw retorne uma resposta textual válida, quando a Urbana
   Connect receber essa resposta, então deve enviá-la pelo fluxo atual de
   WhatsApp.

6. Dado que o OpenClaw retorne resposta vazia, inválida ou acima do tamanho
   máximo configurado, quando o validator mínimo avaliar a resposta, então a
   mensagem não deve ser enviada e o fallback deve ser aplicado.

7. Dado que o OpenClaw não responda dentro do timeout configurado, quando a
   Urbana Connect processar o turno, então o webhook não deve quebrar e o
   fallback deve ser aplicado.

8. Dado que a mensagem inbound seja mídia, grupo ou tipo não suportado nesta
   POC, quando o webhook processar o evento, então o OpenClaw não deve ser
   chamado.

9. Dado que a chamada ao OpenClaw seja executada, quando o processamento
   terminar, então devem existir logs com correlationId, sessionKey, status,
   latência e motivo de erro quando houver.

10. Dado que o OpenClaw esteja configurado como sidecar, quando ele gerar a
    resposta, então ele não deve enviar mensagem diretamente para nenhum canal.
    O envio é responsabilidade exclusiva da Urbana Connect.

---

## 6. Critérios de aceite

1. Existe uma branch e PR contendo a spec da POC.
2. Existe uma decisão técnica registrada após o spike: chamada direta ao
   Gateway ou bridge HTTP mínimo.
3. A Urbana Connect consegue chamar o OpenClaw em ambiente controlado e receber
   resposta textual.
4. O fluxo real do webhook só usa OpenClaw quando a feature flag estiver ligada.
5. A `sessionKey` isola usuários/conversas diferentes.
6. O envio WhatsApp continua passando pela Urbana Connect.
7. Timeout ou falha do OpenClaw não derruba o webhook.
8. Logs mínimos permitem comparar comportamento, erro e latência.
9. Um teste end-to-end em homologação comprova:
   `WhatsApp inbound -> Urbana Connect -> OpenClaw -> Urbana Connect -> WhatsApp outbound`.
10. A equipe consegue avaliar qualitativamente se a conversa ficou melhor que o
    harness atual antes de ampliar escopo.

---

## 7. Edge cases

- Feature flag desligada.
- OpenClaw indisponível.
- OpenClaw lento ou timeout.
- OpenClaw retorna texto vazio.
- OpenClaw retorna resposta excessivamente longa.
- OpenClaw retorna erro técnico no lugar de resposta.
- Mensagem inbound sem texto.
- Mensagem com mídia.
- Mensagem de grupo.
- Usuário envia várias mensagens em sequência.
- Dois usuários diferentes conversam simultaneamente.
- Erro no envio WhatsApp após resposta válida do OpenClaw.
- Duplicidade/retry do webhook da API do WhatsApp.

---

## 8. Observabilidade e validação

### Logs mínimos

Cada turno delegado ao OpenClaw deve registrar:

- `correlationId`;
- identificador da conversa ou telefone normalizado;
- `sessionKey`;
- status da chamada (`success`, `timeout`, `error`, `blocked`, `fallback`);
- latência total;
- motivo do fallback ou bloqueio, quando existir.

Os logs não devem expor tokens, secrets ou dados sensíveis além do necessário
para depuração em homologação.

### Testes automatizados esperados

- Teste unitário do `SessionKeyResolver`.
- Teste unitário do `OpenClawClient` com resposta simulada.
- Teste de timeout/fallback do `OpenClawClient`.
- Teste do validator mínimo para resposta vazia, longa e válida.
- Teste do webhook garantindo que feature flag desligada preserva o fluxo atual.
- Teste do webhook garantindo que feature flag ligada chama OpenClaw para DM
  textual elegível.
- Teste garantindo que mídia/grupo não chama OpenClaw nesta POC.

### Smoke test em homologação

Roteiro mínimo:

1. Enviar mensagem pelo WhatsApp para a conta de homologação.
2. Confirmar que Urbana Connect recebeu o inbound.
3. Confirmar que Urbana Connect chamou OpenClaw com `sessionKey`.
4. Confirmar que OpenClaw respondeu.
5. Confirmar que Urbana Connect enviou a resposta no WhatsApp.
6. Enviar segunda mensagem e confirmar continuidade básica da sessão.
7. Simular OpenClaw indisponível e confirmar fallback sem quebra do webhook.

---

## 9. Plano de implementação

### Fase 1 — Spec e decisão de spike

1. Versionar esta spec no repositório.
2. Abrir PR apenas com a spec.
3. Revisar e aprovar o escopo da POC.
4. Confirmar ambiente onde o OpenClaw Gateway será executado em homologação.

### Fase 2 — Spike técnico

1. Subir OpenClaw Gateway com agente/workspace dedicado da Urba.
2. Validar chamada manual ao OpenClaw.
3. Testar chamada da Urbana Connect para o OpenClaw Gateway.
4. Decidir integração direta ou bridge HTTP mínimo.
5. Registrar a decisão na spec ou em ADR complementar.

### Fase 3 — Integração mínima

1. Implementar `OpenClawClient`.
2. Implementar `SessionKeyResolver`.
3. Implementar validator mínimo.
4. Adicionar feature flag e configurações.
5. Conectar webhook ao `OpenClawClient` para DM textual elegível.
6. Enviar resposta pelo fluxo atual de WhatsApp.
7. Adicionar logs mínimos.

### Fase 4 — Homologação e avaliação

1. Rodar testes automatizados.
2. Subir em homologação com feature flag controlada.
3. Testar conversas reais controladas.
4. Comparar qualidade com o harness atual.
5. Decidir se a próxima fase deve incluir contexto do BD, catálogo, action
   schema, policy validator completo e interface humana.

---

## 10. Dúvidas em aberto

1. A Urbana Connect deve chamar o OpenClaw Gateway diretamente ou usar bridge
   HTTP mínimo?
2. Onde o OpenClaw Gateway ficará hospedado na homologação?
3. Qual modelo será usado no agente OpenClaw da POC?
4. Qual timeout aceitável para resposta no WhatsApp?
5. Qual fallback textual deve ser usado quando o OpenClaw falhar?
6. O fluxo legado deve responder em caso de falha, ou a POC deve usar fallback
   neutro específico?
7. A POC deve persistir outbound gerado pelo OpenClaw no mesmo modelo atual de
   mensagens?
8. Quais conversas reais serão usadas como golden tests qualitativos?

---

## 11. Regra de segurança para agentes

Agentes de IA que implementarem esta POC devem respeitar estas fronteiras:

- não criar múltiplas aplicações sem decisão explícita na fase de spike;
- não expor secrets ou tokens em código, logs ou documentação;
- não permitir envio direto de WhatsApp pelo OpenClaw;
- não ampliar escopo para catálogo, UI ou handoff sem nova spec ou revisão;
- não remover o fluxo atual sem feature flag e fallback;
- preferir PRs pequenos e revisáveis.

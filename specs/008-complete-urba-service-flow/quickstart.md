# Quickstart de validação local

## Pré-condições

- Java 21 e Docker disponíveis;
- dependências do backend e do frontend instaladas;
- Mongo local do compose configurado como fonte canônica;
- imagem/runtime Hermes local configurado para o plugin Urbana;
- variáveis de token apontando apenas para fixtures locais;
- nenhum link real de pagamento, termos ou briefing configurado no ambiente.

## Validação automatizada

Backend:

```bash
cd apps/urbana-connect-api
./gradlew test
./gradlew check
```

Plugin Hermes:

```bash
cd integrations/hermes-agent
python3 -m unittest discover -s plugins/urbana-domain -p 'test*.py'
```

Frontend:

```bash
cd apps/poc-chat
npm test -- --run
npm run build
```

Teste E2E live de qualidade conversacional (requer o stack local real):

```bash
cd apps/poc-chat
PLAYWRIGHT_LIVE=1 PLAYWRIGHT_BASE_URL=http://127.0.0.1:3000 npm run test:e2e -- quality-chat.spec.ts
```

No estado de aceite esperado, o arquivo cria contatos locais efêmeros e cobre
dois contratos distintos: o fluxo conversacional normal, incluindo ICP antes
dos termos e handoff/retomada, e a captura de evidência da POC. O relatório
`quality-chat-transcript.json` é anexado mesmo em falha e contém score,
violações da rubric, ownership e a conversa integral. O teste não usa nomes,
links, pagamentos nem envios externos reais.

O baseline observado antes da implementação cobre descoberta/handoff, mas ainda
não comprova todos os cenários de ICP/termos. Até T024 e T047 estarem verdes,
esse comando é diagnóstico e não evidência final de SC-011.

Teste técnico controlado de SC-014 (fora do fluxo do cliente):

```bash
cd apps/urbana-connect-api
./gradlew test --tests '*StatefulDomainToolServiceTest'
```

Esse teste invoca o boundary de termos com perfil incompleto e deve provar um
evento lógico por chave, resposta comercial inalterada e payload/log sem valor
bruto. Ele não é uma rota elegível de SC-011 e não deve ser reproduzido por uma
conversa real.

Os comandos exatos podem ser adaptados ao wrapper do ambiente, mas a evidência
deve identificar o comando executado e separar falha de código de falha de
dependência/infraestrutura.

## Smoke manual

1. Subir o compose local documentado em `infra/local-poc/README.md`.
2. Abrir a POC e criar uma conversa limpa.
3. Executar o Roteiro A da spec até a confirmação proativa do briefing.
4. Em conversas separadas, executar B, C, D e E, incluindo recusa, segunda
   ausência, captura incidental e atualização silenciosa do ICP.
5. Em cada rota, registrar transcript, modo/ownership, ids de turno/handoff e
   ausência de termos técnicos proibidos.
6. Repetir handoff, retorno e a ação comercial escolhida para verificar
   idempotência.

## Evidências mínimas para aceite

- matriz dos quatro serviços com preço, área, escopo e entregas;
- evidência do checkpoint ICP antes dos termos, da reutilização e da regra de
  `NÃO INFORMADO`;
- screenshot ou export do ack conversacional do handoff;
- registro de que não houve mensagem automática após o ack em modo humano;
- registro de decisão da arquiteta seguida pela retomada;
- transcript da mensagem proativa única, quando o próximo passo é conhecido;
- caso de falha controlada com linguagem segura;
- saída de testes backend, plugin e frontend.
- evento interno `ICP_SKIPPED_BEFORE_TERMS`, quando deliberadamente provocado,
  sem valor bruto do campo nos logs.

Nenhum caso local é evidência de pagamento real, aprovação jurídica de termos,
disponibilidade de agenda ou entrega externa.

## Gates antes da execução e do PR

1. Atualizar `baseline.md`, vincular a subtarefa Jira e iniciar a issue em
   `Em andamento` apenas quando o primeiro escritor começar.
2. Trabalhar em `feature/008-complete-urba-service-flow`, preservando o baseline
   e mantendo `hml` como base.
3. Após todos os testes e roteiros, obter o aceite manual de Emanuel.
4. Abrir PR para `hml` com ticket e evidências; então mover para
   `Awaiting approval`.
5. Não fazer deploy nem promover para `main` por este quickstart.

## Contrato observado pela POC para handoff e controles

Além de `conversation.mode`, a POC aceita os campos seguros abaixo na projeção
`GET /api/poc/conversations/{contactAlias}`:

```json
{
  "conversation": {
    "ownership": "HUMAN",
    "resume": {
      "status": "RECONCILING",
      "retryAllowed": false,
      "failureClass": "HUMAN_CONTEXT_PENDING"
    },
    "pocControls": {
      "approvePaymentProof": true,
      "recordHumanMessage": true,
      "returnToUrba": true
    }
  }
}
```

O ack de handoff deve continuar sendo uma mensagem canônica `OUTBOUND` com
`senderType` `URBA` ou `HUMAN`; o texto de “Aguardando atendimento” é apenas
estado complementar. Campos internos, endpoints e identificadores técnicos
são descartados pelo adaptador.

O backend expõe os endpoints POC de validação humana
`payment-proof/approve`, `human/messages` e `ownership/urba`; o proxy local
libera somente essas rotas de controle, sempre com alias local e token
injetado no servidor. O adaptador também aceita os campos superiores
`ownership`, `resumeStatus`, `resumeId` e `controlAvailability`, normalizando-os
com o formato aninhado para compatibilidade com fixtures legadas. Nenhum
controle envia mensagem do cliente, WhatsApp, e-mail, cobrança ou pagamento
real.

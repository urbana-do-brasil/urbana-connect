# Data Model: Atendimento comercial completo e seguro da Urba

Este documento descreve o contrato lógico que a implementação deve preservar;
os nomes concretos podem seguir os agregados já existentes em `reception`.

## ServiceCatalogItem / ServiceFixture

Fonte canônica dos fatos consumidos pelo atendimento.

| Campo | Regra |
|---|---|
| `serviceType` | `DECOR_INTERIORES`, `DECOR_PINTURA`, `DECOR_FACHADA` ou `DECOR_REFORMA`; nenhum alias legado `DECOR` deve ser apresentado como quinto serviço |
| `name`, `emoji` | apresentação curta |
| `price` | R$ 400, R$ 250, R$ 350 e R$ 450 respectivamente |
| `areaRule` | `UP_TO_20_SQM_PER_ENVIRONMENT` para Interiores/Reforma; `UNLIMITED_BY_CATALOG` para Pintura/Fachada |
| `scope` | escopo afirmativo e diferença com os demais serviços |
| `deliverables` | Manual PDF, Tour Virtual, três opções e duas rodadas |
| `process` | briefing, medidas/mídia, reunião, produção, 7 dias úteis e entrega |
| `responsibilities` | Urba, arquiteta e cliente |
| `exclusions` | execução, compra/contratação, visita/gestão e promessas não autorizadas |
| `support` | três meses para dúvidas sobre Manual e cores, sem visita/gestão |
| `termsResource`, `paymentResource`, `briefingResource` | recurso vigente por ambiente; fixture local não comercial ou recurso aprovado |
| `available` | apenas itens disponíveis podem ser apresentados/contratados |

## ReceptionConversation / contratação

O agregado de conversa continua controlando `mode`, `version`, serviço
selecionado, ambiente, `termsStatus`, `paymentStatus`, estágio comercial e
estado operacional. Regras novas:

- `PRONOUN_PREFERENCE`, `FIRST_TIME_HIRING` e `OCCUPATION` compõem um checkpoint
  conversacional de enriquecimento de lead antes dos termos, sem hard gate de
  backend;
- serviço, ambiente e área aplicável continuam sendo os pré-requisitos de
  estado comercial para termos;
- a thread atual e os fatos correntes fornecem ao SOUL o contexto para oferecer
  no máximo uma segunda oportunidade por campo; o agregado não persiste um
  contador autoritativo de tentativas nem bloqueia termos por ICP;
- declarações explícitas podem ser capturadas incidentalmente em qualquer etapa;
- perfil conhecido é reutilizado entre serviços; o valor explícito mais recente
  substitui silenciosamente o atual, inclusive por `NÃO INFORMADO`;
- mudar serviço antes do pagamento invalida o aceite da contratação anterior;
- dois ambientes/serviços possuem aceites e pagamentos independentes;
- `HUMAN` bloqueia ferramentas e respostas automáticas, mas não impede a
  confirmação canônica do handoff;
- uma retomada precisa de estado separado ou equivalente a
  `PENDING/SYNCHRONIZING/DECIDING/COMPLETED/RETURNED_TO_HUMAN`.

## LeadEnrichment / CustomerFact

O ICP é um perfil global do cliente, independente do serviço contratado. O
modelo deve preservar o valor corrente e o histórico interno sem criar um
snapshot separado de primeira interação.

| Campo | Regra |
|---|---|
| `PRONOUN_PREFERENCE` | texto explícito informado pelo cliente; não categorizar nem inferir |
| `FIRST_TIME_HIRING` | `SIM`, `NÃO` ou `NÃO INFORMADO` |
| `OCCUPATION` | texto explícito informado pelo cliente; não categorizar nem inferir |
| `status` | estado interno `ANSWERED` ou `NOT_INFORMED`; a comunicação usa `NÃO INFORMADO`; estado concluído não volta a ser perguntado automaticamente |
| `source` | mensagem explícita do cliente; fala ou interpretação da arquiteta não é evidência suficiente |
| `capturedAt` | momento da captura da declaração ou conclusão do campo |
| `sourceMessageId` | referência à mensagem que contém a declaração, quando existir |
| `version` / `supersededBy` | histórico de alterações para auditoria; somente o estado atual orienta a próxima pergunta |

Regras de atualização:

- uma declaração explícita posterior substitui o valor corrente sem mensagem
  adicional ao cliente;
- uma recusa posterior pode substituir o valor corrente por `NÃO INFORMADO`;
- o Hermes recebe o histórico integral da thread atual e o contexto associado,
  portanto o prompt deve instruir a considerar a declaração explícita mais
  recente, sem podar mensagens anteriores da mesma thread; threads anteriores
  inteiras não fazem parte desse contexto;
- logs de observabilidade carregam somente campo, status, origem e momento,
  nunca o texto bruto do valor.

## Contexto conversacional do checkpoint (não persistido como autoridade)

O checkpoint ocorre depois da confirmação do serviço e da intenção explícita de
contratar, mas não cria uma entidade `ICPCheckpointState` no backend. O SOUL
conduz a conversa usando:

- a thread atual integral para reconhecer pergunta inicial, resposta parcial,
  segunda oportunidade, assunto paralelo e handoff;
- os fatos correntes para calcular quais dos três campos permanecem ausentes;
- o serviço/estágio comercial já existente para saber quando iniciar ou retomar;
- as mensagens humanas sincronizadas para continuar sem pedir repetição.

Ao voltar de um handoff, o Hermes relê a thread e os fatos atuais e pergunta
somente os campos ainda ausentes. O backend não persiste `status`,
`attemptsByField`, `lastAction` ou outro estado que passe a comandar a redação,
a insistência ou o avanço do diálogo.

## ICPObservationEvent

Registro interno produzido quando a preparação de termos encontra um ou mais
campos do ICP ausentes. É observabilidade somente: não rejeita, não altera o
resultado comercial e não entra no transcript nem no retorno da ferramenta ao
Hermes.

| Campo | Regra |
|---|---|
| `eventType` | valor fixo `ICP_SKIPPED_BEFORE_TERMS` |
| `conversationId` / `turnId` | identificadores opacos para correlação interna |
| `serviceType` | serviço confirmado no momento da detecção |
| `missingFields` | nomes dos campos ausentes, nunca seus valores |
| `detectionPoint` | operação interna em que o desvio foi observado |
| `idempotencyKey` | mesma preparação/replay produz no máximo um evento lógico |
| `occurredAt` | momento da primeira detecção |

O evento não contém valor do perfil, conteúdo de mensagem, transcript, prompt,
URL, recurso comercial, exceção, stack trace ou contador de tentativas.

## CommercialDecision / SafeDomainResult

Resultado interno de uma ferramenta comercial:

```json
{
  "ok": false,
  "code": "MISSING_REQUIRED_CONTEXT",
  "missingFields": ["environment"],
  "nextAction": "ASK_CUSTOMER",
  "customerMessage": "Para seguir, preciso saber qual ambiente você quer transformar."
}
```

`customerMessage` deve ser curto, natural e livre de detalhes técnicos. O
resultado de sucesso pode carregar os dados necessários ao próximo passo, mas
não pode liberar uma etapa que o agregado não confirmou. `MISSING_REQUIRED_CONTEXT`
é reservado a serviço, ambiente, área e proteções comerciais; ICP incompleto
nunca deve gerar esse envelope.

## HandoffRecord

Registro idempotente por conversa/turno:

- motivo e etapa comercial conhecida;
- serviço/ambiente e resumo mínimo para a arquiteta;
- `fromMode`, `toMode`, versão/epoch e ator quando disponível;
- chave de idempotência;
- identificador da confirmação canônica externa e da notificação interna.

Uma repetição deve devolver o registro existente sem nova mensagem ou
notificação.

## ResumeRecord

Registro da transição HUMANO → URBA:

- `resumeId`, `conversationId`, `ownershipEpoch` e `resumeBoundarySequence`;
- checksum/cursor do transcript sincronizado;
- estado e tentativas;
- decisão `PROACTIVE_MESSAGE`, `WAIT_FOR_CUSTOMER` ou
  `RETURN_TO_HUMAN`;
- idempotency key da ação proativa, quando houver;
- classificação interna da falha, sem reproduzi-la ao cliente.

O transcript Mongo é a fonte de verdade. A sessão Hermes é uma projeção
reconstruível e nunca é autoridade para preço, termos ou ownership.

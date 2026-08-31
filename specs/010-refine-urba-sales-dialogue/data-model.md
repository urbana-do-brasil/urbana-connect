# Data model — Refinamento da conversa comercial da Urba

Esta feature cria uma entidade persistente aditiva para não depender de
reconstrução heurística. Os modelos existentes continuam sendo a fonte
operacional, enquanto o registro de consentimento preserva a associação
histórica entre termos apresentados e aceite.

| Entidade | Fonte atual | Uso na feature | Invariantes |
|---|---|---|---|
| `ServiceCatalogItem` / `ServiceFixture` | catálogo canônico Java | preço, área, escopo, entregas, suporte, exclusões e recursos | quatro serviços disponíveis; preços e limites da PEE-102 |
| `ReceptionConversation` | estado operacional Java/Mongo | serviço atual, termos, pagamento, modo e etapa | termos precedem pagamento; comprovante precede aprovação humana |
| `CustomerFact` | fatos versionados Mongo | campos de perfil e ambiente já informados | somente versão atual/reutilizável guia a coleta |
| Transcript de recepção | coleção de mensagens | texto exato de `Aceito`, contexto e ordem de mensagens | mensagens não são reescritas para esconder retrabalho |
| `DomainToolInvocation` | invocações Mongo | recurso/resultado de `prepare_terms` e `prepare_payment` | idempotência e payload preservados |
| `TermsConsentAudit` | nova coleção Mongo `reception_terms_consent_audits` | prova estruturada da apresentação e do aceite por unidade | apresentação durável precede aceite; transição condicional e idempotente; registros são imutáveis após aceite |

## `TermsConsentAudit`

Campos obrigatórios:

- `presentationId` — identidade determinística da apresentação;
- `conversationId`, `contactId`, `turnId`;
- `contractingUnitId` — identificador opaco gerado pelo backend;
- `environmentLabelSnapshot` e `environmentSourceMessageId`;
- `serviceType`;
- `termsResource` e, quando existir, `termsVersion`;
- `prepareTermsInvocationId`, `termsOutboundMessageId`;
- `presentedAt`, `acceptanceMessageId`, `acceptanceEventId`,
  `acceptanceTextExact`, `acceptedAt`, `recordedAt`;
- `status` (`PRESENTED` ou `ACCEPTED`),
  `conversationVersionAtPresentation` e
  `conversationVersionAtAcceptance`.

Índices/garantias:

- único em `presentationId`;
- único esparso em `prepareTermsInvocationId`, evitando duas evidências para a
  mesma preparação de termos sem quebrar documentos legados que não possuem o
  campo;
- único esparso em `acceptanceEventId`, impedindo que o mesmo inbound seja
  associado a duas apresentações;
- consulta por `conversationId + contractingUnitId + status` para localizar a
  apresentação corrente;
- atualização aceita somente de `PRESENTED` para `ACCEPTED`; o primeiro aceite
  válido vence e replays retornam o mesmo registro.

O backend cria `contractingUnitId` somente quando o ambiente foi explicitado de
forma inequívoca no contexto. Uma etiqueta sozinha não é identidade suficiente
para dois ambientes iguais; mensagens ambíguas permanecem em esclarecimento.

## Evidência mínima do aceite

Um aceite válido deve poder ser lido diretamente no registro estruturado e
conferido contra o transcript/invocações:

1. serviço e unidade/ambiente identificável;
2. recurso (e versão, quando disponível) apresentado;
3. timestamps e IDs de apresentação e aceite;
4. mensagem inbound textual exata;
5. transição da conversa para `ACCEPTED` antes de `PREPARED`.

Registros legados sem `TermsConsentAudit` não são backfillados por proximidade
de mensagens. O fluxo deve reapresentar os termos e criar uma nova evidência.

## Transições relevantes

```text
service selected -> TERMS/PRESENTED -> ACCEPTED -> PAYMENT/PREPARED
PAYMENT/PREPARED -> PROOF_RECEIVED -> CONFIRMED/BRIEFING (somente humano)
```

O player de quantidade não é uma entidade nem uma transição nesta feature.

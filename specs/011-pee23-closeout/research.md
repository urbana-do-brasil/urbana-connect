# Research: fechamento operacional da PEE-23

## Evidências do baseline capturado antes da implementação

Os itens abaixo registram o ponto de partida que motivou o plano: webhook com
fallback para a state machine, aceite textual, ausência de tela/API web,
falhas Hermes sem handoff durável, gateway WhatsApp sem provider ID, catálogo
canônico sem overlay operacional e nenhum adapter de pagamento.

## Resultado técnico da implementação local

- O webhook agora falha fechado sem Hermes; o controller não roteia para a
  state machine, e o serviço legado fica condicionado ao perfil desativado.
- O consentimento web possui sessão efêmera, auditoria append-only, token
  digest/HMAC, binding por conversa/serviço/versão, fim de leitura reconhecido
  pelo servidor e decisão idempotente. Texto no WhatsApp não libera pagamento.
- Falhas Hermes criam intenção `EMAIL_HUMAN_HANDOFF` em outbox antes da
  mensagem segura aprovada. A mensagem ao cliente não contém detalhe técnico;
  a posse HUMAN e a entrega exigem recuperação pelo worker. Respostas normais
  Hermes e a mensagem segura também são intents idempotentes; o webhook oficial
  enfileira antes de executar, e retry/reconciliação não redisparam Hermes às
  cegas.
- O resultado de envio WhatsApp exige provider ID; pré-dispatch pode ser
  reprocessado com backoff, enquanto pós-dispatch desconhecido fica
  `AMBIGUOUS`, sem retry cego. O worker só é ativado quando resolver de destino
  e adaptadores reais forem configurados. O endereço WhatsApp é associado ao
  contato opaco em uma coleção interna idempotente na entrada; a outbox não
  armazena telefone nem tenta reverter hash.
- O webhook oficial enfileira mídia reconhecida, incluindo comprovantes; uma
  imagem/documento recebido com pagamento preparado é convertido em evidência
  `PROOF_RECEIVED` e atendimento humano sem confirmação. Turnos
  `BLOCKED_BY_HUMAN` recuperam o ACK persistido no transcript e o publicam como
  `WHATSAPP_HUMAN_HANDOFF_ACK` idempotente; falha de outbox após um turno
  `COMPLETED` consulta o recibo finalizado e republica sem redispatchar Hermes.
- O catálogo efetivo combina baseline por ambiente com override Mongo sem
  sobrescrever documento existente; falha do Mongo usa baseline e é registrada.
  HML/PROD rejeitam fixture quando o catálogo operacional está incompleto.
- Não foi criado adapter de pagamento: a decisão do provedor e a execução
  técnica continuam bloqueadores explícitos de T040–T043.
- O ledger de invocações mantém apenas um snapshot redigido para recursos
  bearer (`#t=`/`token=`); a resposta em memória não é usada como histórico.

O conjunto legado da state machine permanece compilável apenas para preservar
componentes de canal e testes históricos, mas o bean é condicionado a
`hermes.poc.enabled=false` e o controller não possui fallback. A remoção física
de classes exclusivas deve ser uma limpeza serial posterior, sem apagar os
gateways compartilhados sem uma migração própria.

## Decisões técnicas resultantes

1. Servir a UI no mesmo origin do backend reduz CORS e evita um novo deploy.
2. Token no fragmento, removido do histórico, reduz vazamento em logs/Referer.
3. Sessão efêmera e auditoria append-only são modelos distintos: o link expira,
   a evidência jurídica não expira junto.
4. Outbox é necessária para continuar WhatsApp/e-mail sem chamar serviços
   externos dentro da transação; provider sem idempotência não permite retry
   cego após timeout.
5. A escolha do provedor fica fora da implementação genérica até a decisão
   registrada em T040; a discovery técnica não autoriza seleção automática.

## Dependências não resolvidas por código

Documentos/versões jurídicos, base URL/ingress, `destinationRef`, mailbox HML,
credenciais, contrato do provedor, política de retenção e cluster HML precisam
ser fornecidos/validados durante a execução.

## Discovery de provedores — 2026-09-03 (T039)

### Contrato usado na comparação

A comparação considera somente o que é necessário para a fronteira desta
história: gerar e enviar ao cliente um checkout hospedado para uma contratação
de serviço, sem confirmar pagamento. O candidato precisa permitir teste sem
cobrança em HML, possuir credenciais/configuração separadas para PROD, expor
identificador e estado do recurso e oferecer um caminho documentado para
notificações futuras. PIX e cartão são desejáveis, mas não são filtro automático;
uma limitação deve ser aprovada pelo PO/negócio antes do adapter.

Os preços abaixo são apenas sinais públicos encontrados na documentação/página
comercial na data da pesquisa. Eles não substituem proposta, análise de taxas
de recebimento, antecipação, parcelamento, chargeback, KYC ou contrato da Urba.

### Matriz executiva

| Candidato | PIX/cartão e checkout | HML sem cobrança e promoção para PROD | Idempotência, IDs, estados e webhook futuro | Custo/comercial (sinal público) | Leitura para a PEE-23 |
| --- | --- | --- | --- | --- | --- |
| **Asaas** | Link hospedado suporta boleto, PIX e cartão; atende o fluxo de serviço avulso. [`Link de pagamento`](https://docs.asaas.com/docs/link-de-pagamentos) | Sandbox independente, com URL/base e chave próprias; não movimenta valores reais e exige reconfiguração na produção. [`Sandbox`](https://docs.asaas.com/docs/sandbox) | A API retorna IDs de cobrança/link e estados; webhooks e reprocessamentos são documentados como idempotentes. Confirmar a chave idempotente da operação de criação no spike do adapter. | Página comercial informa emissão do link sem taxa e tarifas por cobrança; condições são por conta/período. [`Preços do link`](https://www.asaas.com/link-pagamento) | **Aderência alta.** API simples e separação de ambientes clara. Validar conta, limites, método por link e proposta comercial. |
| **PagBank** | Checkout/link hospedado com cartão, débito, PIX, boleto e carteira PagBank; permite configurar os meios, mas “Pagar com PagBank” permanece disponível. [`Checkout e Link`](https://developer.pagbank.com.br/docs/checkout) | Sandbox não tem valor monetário e oferece cartões/simulador. Para PROD, a homologação PagBank é obrigatória (SLA publicado de até 4 dias úteis). [`Testes`](https://developer.pagbank.com.br/docs/testar-integracao) · [`Homologação`](https://developer.pagbank.com.br/docs/solicitar-homologacao) | Criação devolve `id` e URL `links[].rel=PAY`; há notificações de checkout/pagamento com estados como `PAID`, `WAITING`, `DECLINED`, `CANCELED` e `EXPIRED`; a API documenta chave de idempotência. [`Criar checkout`](https://developer.pagbank.com.br/reference/criar-checkout) · [`Webhooks`](https://developer.pagbank.com.br/reference/webhooks-checkout) · [`Idempotência`](https://developer.pagbank.com.br/docs/chaves-publicas-e-de-idempotencia) | Página pública exibe exemplos de tarifas por prazo e PIX, mas ressalva variação por negociação/elegibilidade. [`Taxas online`](https://pagbank.com.br/para-seu-negocio/online/checkout) | **Aderência alta.** Muito adequado ao link hospedado; a homologação obrigatória e a carteira que não pode ser ocultada precisam ser aceitas. |
| **Pagar.me / Stone** | Link de pagamento do tipo `order` aceita `credit_card`, `boleto` e `pix`; retorna URL, ID e status. [`Link/Checkout`](https://docs.pagar.me/reference/checkout-link) | Conta de teste usa `sdx-api`/`sk_test`; produção usa endpoint/credencial próprios. [`Criar link`](https://docs.pagar.me/reference/criar-link) | Idempotência é suportada, com janela documentada diferente entre teste e produção; eventos incluem `order.paid`, falha e estados do checkout. [`Idempotência`](https://docs.pagar.me/docs/o-que-%C3%A9) · [`Eventos`](https://docs.pagar.me/reference/eventos-de-webhook-1) | Não encontrei tarifa pública comparável no material técnico; solicitar proposta considerando o modelo de conta. | **Aderência alta com ressalva.** PIX depende da conta/gateway habilitado, e a migração/versão V5 deve ser confirmada antes de codificar. |
| **Mercado Pago** | Checkout Pro/link hospedado via preferência e `init_point`; cartão e PIX são testáveis, junto com outros meios da carteira. [`Preferências`](https://www.mercadopago.com.br/developers/pt/docs/checkout-pro/checkout-customization/preferences) | Usa contas de teste vendedor/comprador, cartões fictícios e compras sem cobrança real; URLs de webhook de teste e produção devem ser distintas. [`Contas/testes`](https://www.mercadopago.com.br/developers/pt/docs/checkout-pro/integration-test) · [`Webhooks`](https://www.mercadopago.com.br/developers/en/docs/checkout-pro-orders/resources/notifications/webhooks) | Preferências/pagamentos têm IDs e estados; idempotência e assinatura de webhook estão disponíveis. A confirmação continuaria fora desta história. | A página pública do link lista, como referência, 4,98% no cartão à vista e 0,99% no PIX, variando por prazo/conta. [`Tarifas do link`](https://www.mercadopago.com.br/ferramentas-para-vender/link-de-pagamento) | **Aderência média/alta.** Tecnicamente viável, porém o setup de contas de teste e a presença de meios adicionais da carteira aumentam a decisão comercial/produto. |
| **Efí** | Link hospedado permite boleto, cartão e PIX; endpoint one-step retorna `payment_url`. [`Link de pagamento`](https://dev.efipay.com.br/docs/api-cobrancas/link-de-pagamento/) | `sandbox=true` usa credenciais distintas; PIX sandbox simula estados e possui regras de valor; produção requer credenciais/certificados próprios. [`Pix CobV/teste`](https://dev.efipay.com.br/docs/api-pix/cobrancas-com-vencimento/) | IDs/status e notificações existem; PIX usa webhook com mTLS/certificado e cartão exige tokenização/KYC. | Tarifas públicas existem, mas dependem de produto/conta; solicitar proposta formal antes de comparar margem. | **Aderência média.** Cobre o requisito, mas KYC, certificado/mTLS e regras de sandbox elevam o custo operacional da primeira entrega. |
| **Stripe (condicional)** | Payment Links hospedados e mais de 40 meios; PIX no Brasil está marcado como “invite only”. [`Payment Links`](https://docs.stripe.com/payment-links/create) · [`Suporte de meios`](https://docs.stripe.com/payments/payment-methods/payment-method-support) | Test mode/sandbox e chaves separadas; webhooks e idempotência são maduros. [`Chaves`](https://docs.stripe.com/keys) · [`Idempotência`](https://docs.stripe.com/api/idempotent_requests) | IDs/estados e `checkout.session.completed` para o futuro webhook. | Exige cotação/eligibilidade local; não há motivo técnico para assumir disponibilidade de PIX. | **Condicional/baixa prioridade.** Só permanece na lista se a conta da Urba for elegível ao PIX; não deve ser escolhido como padrão sem essa confirmação. |

### Recomendação técnica provisória

Após a revisão do PO, a shortlist de aprofundamento desta rodada passou a ser
**Asaas, PagBank e Mercado Pago**. Pagar.me, Efí e Stripe permanecem somente
como referências da matriz ampla; nenhum deles está autorizado a entrar no
adapter sem uma nova decisão. Os três finalistas cobrem o link hospedado e os
dois meios desejados, mas a escolha entre eles depende de taxa, KYC, suporte,
contrato, prazo de recebimento, meios adicionais e caminho de homologação.

Essa é uma recomendação técnica, não uma decisão de negócio. Nenhum provedor
foi selecionado, nenhum contrato/credencial foi criado e nenhum adapter ou
deploy de pagamento deve ser iniciado antes de T040. A decisão precisa registrar
explicitamente: provedor, método(s) aceitos, eventual checkout único, custos e
limitações aceitas, estratégia de contas HML/PROD, rotação de segredos e dono da
homologação/conta.

### Próximo gate de decisão (T040)

O PO/negócio deve escolher um candidato da shortlist ou pedir uma investigação
adicional com base nos fatores comerciais. Depois disso, a execução técnica deve
validar no sandbox: criação de um recurso de teste por serviço/método, URL
HTTPS compartilhável no WhatsApp, IDs/estado retornados, expiração, retries e a
configuração equivalente de produção. A confirmação de pagamento, webhook e
reconciliação continuam fora da PEE-23.

## Aprofundamento da shortlist do PO — Asaas, PagBank e Mercado Pago (2026-09-03)

O relatório consolidado, com o fluxo proposto para a Urba, comparação de
sandbox/HML/PROD, limitações de método, idempotência, webhooks futuros e
simulação de tarifas em uma venda de R$ 1.000, está em
`docs/plans/pee-107-discovery-providers.html`.

### Resultado técnico resumido

- **Asaas:** `POST /v3/checkouts` cria uma página hospedada; `billingTypes` pode
  receber `PIX`, `CREDIT_CARD` ou ambos, e `minutesToExpire` aceita 10–1.440
  minutos. O Checkout recebe `externalReference`, devolve ID/URL e tem eventos
  próprios para a etapa futura. Sandbox e Produção são contas/base
  URLs/chaves independentes. É o caminho aparentemente mais simples para um
  primeiro adapter, mas a página geral de preços e a página promocional de link
  exibem condições diferentes, que precisam ser confirmadas na conta PJ.
- **PagBank:** `POST /checkouts` devolve `id` e URL `links[].rel=PAY`; Sandbox
  não tem valor monetário e permite cartões/simulador; a homologação antes de
  PROD é obrigatória. O checkout aceita PIX e cartão, mas “Pagar com PagBank”
  permanece disponível mesmo quando outros meios são configurados.
- **Mercado Pago:** `POST /checkout/preferences` devolve `init_point`; contas
  vendedor/comprador e cartões de teste permitem validar o Checkout Pro, e
  PIX/boleto offline podem permanecer pendentes no teste. Webhooks de teste e
  produção são separados e assinados; o recebimento imediato e as tarifas
  dependem da conta/produto.

A referência técnica principal do Asaas nesta revisão é a documentação do
[Checkout hospedado](https://docs.asaas.com/reference/create-new-checkout):
ela confirma que `billingTypes` aceita PIX e cartão no mesmo recurso, que
`minutesToExpire` aceita 10–1.440 minutos e que a criação retorna apenas a
página de pagamento. A confirmação financeira continua assíncrona e deve ser
tratada por eventos em uma história posterior.

### Sinais públicos de tarifa

Os valores não são proposta comercial e não devem ser comparados sem equalizar
prazo de recebimento e parcelamento:

- Asaas: PIX R$ 1,99/transação; cartão à vista R$ 0,49 + 2,99%; 2–6 parcelas
  R$ 0,49 + 3,49%; 7–12 R$ 0,49 + 3,99%; 13–21 R$ 0,49 + 4,29%. A página
  também mostra promoções válidas por três meses (0,99%/1,99% etc.) e informa
  antecipação de cartão de 1,25% ao mês sujeita a análise.
- PagBank: PIX 1,89%; débito 2,39%; crédito à vista 4,99% + R$ 0,40 em
  D+14 ou 3,99% + R$ 0,40 em D+30; parcelamento sem acréscimo ao cliente
  adiciona 2,99% ao mês ao vendedor. A página ressalva negociação/elegibilidade.
- Mercado Pago: PIX 0,99%; cartão 4,98% na hora, 4,48% em 14 dias ou 3,99%
  em 30 dias; saldo/linha de crédito 4,99%; boleto 3,99% em até 3 dias. Há
  tarifa adicional quando o vendedor oferece parcelas sem acréscimo, e novos
  vendedores podem não ter recebimento imediato.

As fontes oficiais e a simulação líquida de R$ 1.000 estão no HTML. T040 ainda
é uma decisão de negócio: registrar provedor, meios/limitações, teto de taxa,
prazo de recebimento, conta responsável, evidências HML e plano de PROD. Sem
isso, T041–T043 continuam bloqueadas e nenhum segredo/adapter deve ser criado.

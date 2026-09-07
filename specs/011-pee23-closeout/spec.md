# Feature Specification: Fechamento operacional da PEE-23

**Feature Branch**: `011-pee23-closeout`

**Created**: 2026-09-02

**Status**: Aprovada por Emanuel — pronta para planejamento/implementação

**Input**: decisões refinadas no documento [pee-23-decisoes-grill.html](../../docs/plans/pee-23-decisoes-grill.html)

**Ticket**: PEE-23 — Urba: Fluxo de Triagem e Vendas

## 1. Objetivo e limite

Concluir o fluxo comercial da Urba no WhatsApp, com Hermes como única
arquitetura de atendimento, desde a saudação/triagem até a preparação e o
envio de um checkout do serviço escolhido. A história termina no checkout
disponibilizado ao cliente; ela não confirma o pagamento nem inicia o briefing.

Esta feature consolida as decisões de negócio aprovadas e fecha os gaps
operacionais encontrados na implementação atual. A discovery e a escolha do
provedor de pagamento permanecem uma subtask dependente; nenhum provedor é
inventado nesta spec.

## 2. Regras de negócio aprovadas

### 2.1 Catálogo e contratação

- Os serviços canônicos são **Decor Interiores**, Decor Pintura, Decor Fachada e
  Decor Reforma. O nome comercial legado `Decor` não deve aparecer para o
  cliente.
- HML oferece os quatro serviços com recursos de teste/sandbox. PROD só oferece
  um serviço quando preço, termos e checkout de produção estiverem válidos;
  serviço sem recurso válido não é ofertado e pode ser encaminhado para humano.
- Cada checkout representa um serviço. Troca de serviço invalida o aceite
  anterior e reinicia preço, termos e pagamento.
- O baseline é definido por propriedades do ambiente e o MongoDB é o override
  operacional. No primeiro deploy, registros ausentes podem ser inicializados
  pelo baseline; registros existentes nunca são sobrescritos por um restart.
  Se o Mongo estiver indisponível, o baseline do ambiente é o fallback seguro,
  com observabilidade.

### 2.2 Termos e aceite

- Cada serviço possui recurso e versão de termos próprios, ainda que o texto
  seja igual hoje. HML e PROD exibem o mesmo conteúdo jurídico aprovado; só o
  checkout varia por ambiente.
- Depois de serviço e intenção confirmados, o WhatsApp envia um link HTTPS para
  uma tela que renderiza o texto integral da versão vigente.
- O botão é **“Li e aceito os termos”** e só habilita após o cliente percorrer o
  conteúdo até o fim. O scroll é evidência de interação, não prova de leitura.
- O aceite textual no WhatsApp nunca libera pagamento. Se o cliente escrever
  “aceito”, o sistema reenvia o link da tela.
- O cliente pode clicar em **“Não aceito”**. Isso registra recusa, não libera o
  pagamento e retorna ao menu do WhatsApp.
- Fechar a página, perder conexão ou deixar o link expirar não registra recusa.
  O atendimento fica pendente; uma interação posterior pode emitir novo link ou
  voltar ao menu.
- Registrar separadamente apresentação, chegada ao final, aceite, recusa e
  expiração, sempre vinculados ao contato, ambiente, serviço, versão/hash,
  contratação e instantes correspondentes.
- Se serviço ou versão mudar antes do pagamento, o aceite anterior deixa de ser
  válido e o cliente deve aceitar novamente.
- Após aceite, a página confirma o resultado e o sistema retoma automaticamente
  o WhatsApp com as opções de pagamento.

### 2.3 Pagamento

- A escolha de PIX ou cartão continua no WhatsApp, por opções interativas. O
  checkout deve corresponder à opção escolhida; um checkout único que ofereça a
  escolha internamente é uma limitação a ser explicitamente aprovada.
- A discovery do provedor deve comparar PIX, cartão, sandbox sem cobrança,
  operação HML/PROD, custos, credenciais, rotação de segredos, idempotência,
  consulta de status e caminho futuro de webhook.
- PIX e cartão são requisitos desejáveis, não filtro automático. Limitações do
  provedor ficam registradas para decisão do PO/negócio; sem decisão explícita,
  a PEE-23 não pode ser encerrada.
- HML usa sandbox/teste e PROD terá configuração/credenciais próprias, sem
  alteração de código para promover a mesma implementação. Produção não é
  ativada nesta história.
- Confirmação de pagamento, webhook/reconciliação financeira, validação de
  comprovante como confirmação e liberação de briefing ficam fora do escopo.

### 2.4 Continuidade e atendimento humano

- Hermes é a única fonte de verdade. O fallback condicional para a state machine
  legada e o código exclusivo desse caminho devem ser removidos; componentes
  compartilhados só permanecem se necessários ao Hermes.
- Falhas são classificadas por fase/ação: pré-dispatch, transitória retryável
  com idempotência, pós-dispatch ambígua (reconciliar antes de agir), saída
  inválida, entrega e persistência. O cliente não recebe detalhe técnico.
- Quando não houver recuperação segura, enviar exatamente:
  “Recebemos sua mensagem. Vamos encaminhar seu atendimento para nossa equipe,
  que entrará em contato para ajudar.”
- A falha também gera notificação por e-mail, posse HUMAN e registro durável.
  A mensagem ao cliente e o e-mail devem ser idempotentes e recuperáveis; não
  há promessa de SLA.
- HML usa caixa/alias de teste separado de PROD. Endereço e parâmetros SMTP são
  insumos solicitados na execução.
- Comprovante recebido nunca confirma pagamento nem libera briefing; deve ir
  para atendimento humano.

## 3. Requisitos funcionais

- **FR-001 — Fluxo Hermes:** novas conversas, retomadas ativas e conversas
  expiradas percorrem saudação, triagem guiada/direta, serviço, confirmação,
  termos web, meio de pagamento e checkout sem invocar a implementação legada.
- **FR-002 — Catálogo operacional:** preço, nome, disponibilidade, termos e
  recursos de checkout são lidos do catálogo efetivo, respeitando override
  Mongo, baseline por ambiente e fallback observável.
- **FR-003 — Emissão de consentimento:** `prepare_terms` deve criar uma sessão
  efêmera ligada à conversa, contato, contratação, serviço, ambiente, versão e
  hash do conteúdo; gerar token aleatório não derivado de PII; persistir o
  evento de emissão e enviar o link por outbox.
- **FR-004 — Visualização:** a página HTTPS recupera somente o recurso ligado ao
  token, renderiza conteúdo escapado e não expõe token em logs, histórico,
  `Referer` ou cache. Deve funcionar em mobile, teclado e leitor de tela.
- **FR-005 — Eventos da tela:** registrar `PAGE_PRESENTED` e `END_REACHED`
  separadamente e aceitar `ACCEPT` somente depois de `END_REACHED`. Repetição
  da mesma decisão é idempotente; decisão oposta não reescreve a primeira.
- **FR-006 — Decisão:** `ACCEPT` registra auditoria, altera a conversa de forma
  transacional e cria outbox para a pergunta PIX/cartão; `DECLINE` registra
  recusa e cria outbox de retorno ao menu; expiração/abandono não criam recusa.
- **FR-007 — Continuação:** o callback web nunca chama Hermes ou WhatsApp de
  forma síncrona dentro da transação. Um worker envia mensagens usando um
  `destinationRef` resolvível e registra provider ID, tentativas e estado.
- **FR-008 — Handoff:** qualquer falha sem recuperação segura cria uma única
  notificação ao cliente e uma notificação de e-mail, com retry durable e posse
  HUMAN. Se WhatsApp estiver indisponível, o e-mail/alerta permanece a garantia
  possível.
- **FR-009 — Resiliência:** falhas Mongo não produzem falso aceite ou falso
  avanço. Fallback de propriedades é permitido somente para dados aprovados;
  ausência de recurso válido encaminha para humano.
- **FR-010 — Aceite funcional:** PEE-92 deve validar no HML o checklist completo
  com transcrições/evidências atuais, incluindo quatro serviços, termos web,
  recusa, abandono, sandbox, retomada, expiração e handoff.

## 4. Segurança, privacidade e observabilidade

- Token com entropia criptográfica mínima de 192 bits, validade curta
  parametrizada (valor inicial sugerido: 30 minutos) e uso único por decisão.
  Persistir digest/HMAC; nunca colocar PII no token ou na URL.
- Preferir link com token no fragmento (`/termos#t=...`), removido do histórico
  assim que a página carregar; usar API same-origin com header de autorização.
  Qualquer fallback para path/query precisa de `no-referrer` e redaction de
  logs validada em HML.
- Página e API usam `Cache-Control: no-store`, `Referrer-Policy: no-referrer`,
  CSP estrita sem terceiros, `frame-ancestors 'none'`, `nosniff`, `noindex` e
  HTTPS. CORS permanece fechado para origem não autorizada.
- Conteúdo jurídico é renderizado de forma estruturada/escapada; HTML vindo do
  Mongo não pode ser inserido cegamente no DOM.
- A auditoria armazena somente dados necessários ao aceite. Token, URL completa,
  Authorization, IP e user-agent não entram em logs/auditoria por padrão.
- O prazo de retenção da auditoria é parametrizado e permanece pendente de
  definição da Urba/Jurídico antes da produção.
- Logs e métricas devem permitir distinguir falha pré-dispatch, ambígua,
  entrega, persistência e handoff sem registrar conteúdo sensível.

## 5. Critérios de aceite da história

1. Os quatro serviços corretos são apresentados e respeitam disponibilidade,
   preço e recursos do ambiente.
2. HML exibe os termos jurídicos aprovados de cada serviço em tela HTTPS; o
   botão só habilita após o fim do conteúdo e o aceite gera auditoria completa.
3. Texto “aceito” no WhatsApp, aceite antecipado, link expirado e decisão
   duplicada não liberam pagamento; recusa explícita retorna ao menu.
4. Aceite aprovado retoma automaticamente o WhatsApp com PIX/cartão e um
   checkout correspondente ao método escolhido quando o provedor suportar essa
   modalidade; checkout único com seleção interna só é permitido após limitação
   explicitamente aprovada, sem confirmação financeira automática.
5. Falha do Hermes, página, Mongo, WhatsApp ou e-mail segue a classificação,
   retry/reconciliação e handoff definidos, sem duplicidade ou detalhe técnico
   para o cliente.
6. O catálogo mantém alteração Mongo após restart e usa baseline do ambiente
   quando Mongo estiver indisponível; o seeder não sobrescreve operação.
7. HML foi validado com deploy corrente, secrets/SMTP, WhatsApp real de teste,
   link sandbox, transcripts e checklist PEE-92 aprovado.
8. PEE-93 (termos/tela), subtask do provedor e nova subtask de validação HML
   estão concluídas; suas evidências estão anexadas antes da transição da
   PEE-23.

## 6. Dependências e insumos bloqueantes

- Quatro documentos/URLs oficiais, versões, hashes e aprovação jurídica.
- Discovery e decisão do provedor; credenciais sandbox e plano/credenciais PROD.
- Base URL HTTPS pública e ingress de HML/PROD.
- `destinationRef` resolvível para o contato WhatsApp e contrato de provider ID
  / idempotência do canal.
- Caixa/alias SMTP de HML, parâmetros de conexão e destinatário de produção.
- Política de retenção jurídica.
- Acesso ao cluster e número/contato de WhatsApp de teste.

## 7. Fora de escopo

Confirmação automática de pagamento, webhook/reconciliação financeira,
validação de comprovante como confirmação, briefing, agendamento, onboarding,
ativação de produção, descontos/pacotes e qualquer fluxo pós-pagamento.

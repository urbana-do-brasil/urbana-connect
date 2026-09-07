# Handoff de retomada — PEE-23 / PEE-107

**Atualizado em:** 2026-09-07
**Branch de trabalho:** `011-pee23-closeout`
**Registro Jira:** [PEE-23](https://urbanadobrasil.atlassian.net/browse/PEE-23) e [PEE-107](https://urbanadobrasil.atlassian.net/browse/PEE-107)

## Onde a sessão parou

A implementação local do fechamento operacional da PEE-23 foi construída para
consentimento web auditável, fluxo Hermes-only, handoff humano recuperável e
catálogo configurável por ambiente. Os testes locais e os manifests HML/PROD
foram validados. Não houve deploy, uso de credencial real ou chamada para
produção.

A subtask PEE-107 concluiu a discovery documental e deixou **Asaas, PagBank e
Mercado Pago** como finalistas. O Asaas é a preferência técnica provisória por
aderência e taxas públicas, mas **nenhum provedor foi escolhido formalmente**.
A PEE-107 permanece `Em andamento` e a PEE-23 permanece `Tarefas pendentes`.

## Próximo passo exato

O próximo passo é executar o **smoke exploratório do Asaas Sandbox**, caso o PO
autorize o teste. Esse smoke deve acontecer antes de qualquer adapter e serve
para gerar evidência suficiente para a decisão T040.

1. Confirmar autorização para testar o Asaas somente no Sandbox, sem interpretar
   isso como contratação definitiva.
2. Obter uma conta Sandbox, disponibilizar a API key em um secret manager ou
   variável protegida e definir callbacks HTTPS. Nunca registrar a chave no
   chat, Jira, documentação ou repositório.
3. Confirmar os quatro serviços e os valores que serão simulados: Decor
   Interiores (R$ 400,00), Decor Pintura (R$ 250,00), Decor Fachada (R$ 350,00)
   e Decor Reforma (R$ 450,00), salvo alteração aprovada.
4. Criar e abrir um Checkout avulso com Pix e cartão, validade de 30 minutos,
   usando somente dados sintéticos. Registrar de forma sanitizada o ID, URL,
   estado, expiração, serviço, valor e meios exibidos.
5. Repetir a verificação para os quatro serviços e registrar falhas,
   limitações e eventual comportamento de duplicidade. A proteção de
   idempotência da aplicação só será validada depois, na integração HML.
6. Atualizar `research.md` e o Jira com o resultado. Somente após a decisão
   T040 devem começar T041 (contrato/testes), T042 (adapter/configuração) e
   T043 (`prepare_payment`).

## O que permanece bloqueado

- T040: decisão do provedor e limitações comerciais aprovadas.
- T041–T043: contrato, adapter e integração de pagamento.
- T047: validação técnica no HML com recursos reais, segredos e dependências
  operacionais.
- T048/T050: checklist de aceite, evidências finais e recomendação de transição.

O código atual não possui adapter de pagamento. Portanto, uma sessão nova não
deve tentar configurar segredo de Asaas no HML nem marcar a PEE-23 como concluída
antes de executar o smoke, registrar T040 e cumprir os gates HML/PO.

## Validação da consolidação

Nesta sessão, `git diff --cached --check`, parse do fluxograma HTML,
`kubectl kustomize` para HML/PROD, os 14 testes do plugin Hermes, as 18
execuções/92 asserções do corpus e os 3 testes Playwright passaram. O Gradle
compilou produção e testes, mas o `check` não terminou porque os testes
Testcontainers ficaram aguardando o Docker local indisponível; isso deve ser
reexecutado em uma máquina com Docker antes de declarar a suíte Java verde.

## Arquivos de retomada

- `specs/011-pee23-closeout/tasks.md` — ordem das tarefas e dependências.
- `specs/011-pee23-closeout/research.md` — discovery e matriz de provedores.
- `specs/011-pee23-closeout/quickstart.md` — comandos e evidências locais/HML.
- `docs/plans/pee-107-discovery-providers.html` — relatório comparativo.
- `docs/plans/asaas-sandbox-flow.html` — fluxograma visual dos gates.

Ao iniciar uma nova sessão, ler este arquivo, verificar `git status` e continuar
no smoke do Sandbox ou, se a autorização ainda não existir, preparar somente os
insumos listados acima. O commit que contém este handoff é a base de retomada da
branch.

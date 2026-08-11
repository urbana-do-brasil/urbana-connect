# Spec SDD — Recebimento de webhook WhatsApp

## Metadados

- `Título da feature`: Recebimento mínimo de webhook WhatsApp em homolog
- `Ticket Jira`: referência histórica às subtarefas `PEE-37`, `PEE-38`, `PEE-48` e `PEE-67`
- `Status`: implementado
- `Responsável pela spec`: engenharia Urba
- `Data`: 2026-04-01

## 1. Contexto

A Urba precisava receber eventos do WhatsApp Business em homolog antes de iniciar qualquer feature de negócio. Para isso, era necessário disponibilizar um endpoint público, validar o challenge exigido pela Meta e aceitar apenas o payload mínimo esperado do provider.

Além disso, a homolog precisava oferecer rastreabilidade suficiente para confirmar que eventos reais estavam chegando ao sistema sem expor payload sensível completo em logs.

## 2. Comportamentos esperados

1. Quando a Meta chamar `GET /api/webhook` com `hub.mode=subscribe`, token válido e `hub.challenge`, o sistema deve responder `200` com o valor de `hub.challenge`.
2. Quando a Meta chamar `GET /api/webhook` com token inválido, o sistema deve responder `403`.
3. Quando um `POST /api/webhook` chegar com `object=whatsapp_business_account`, o sistema deve responder `200`.
4. Quando um `POST /api/webhook` chegar com provider diferente do esperado, o sistema deve responder `400`.
5. O recebimento do challenge e do `POST` válido ou inválido deve gerar log explícito com metadados seguros.
6. O endpoint deve permanecer acessível publicamente em homolog através de `api-hml.urbanadobrasil.com`.

## 3. Critérios de aceite

- `GET /api/webhook` com token válido retorna `200` e devolve o challenge.
- `GET /api/webhook` com token inválido retorna `403`.
- `POST /api/webhook` com payload mínimo válido retorna `200`.
- `POST /api/webhook` com payload inválido retorna `400`.
- o webhook está acessível publicamente em homolog com DNS e TLS válidos.
- há evidência operacional de que um evento real chegou via webhook.
- os logs do controller registram o recebimento do challenge e do `POST` sem despejar payload completo.

## 4. Edge cases

- token de verificação incorreto
- payload sem `object`
- payload com `object` diferente de `whatsapp_business_account`
- entrada com `entry` vazia
- endpoint público indisponível por falha de DNS, TLS, ingress ou deploy

## 5. Observabilidade e validação

- testes WebMvc cobrindo:
  - challenge válido
  - challenge inválido
  - `POST` válido
  - `POST` inválido
  - logs explícitos do controller
- validação pública em homolog com o script `infra/kubernetes/apps/urbana-connect-api/overlays/hml/validate-public-webhook.sh`
- confirmação manual de evento real recebido em homolog por horário e log

## 6. Fora de escopo

- processamento funcional da mensagem recebida
- persistência da mensagem ou da conversa
- resposta automática ao usuário
- integração com regras de negócio ou IA

## 7. Dúvidas em aberto

- quais identificadores de mensagem devem ser logados no futuro para rastreabilidade mais forte sem expor conteúdo sensível?
- quando o webhook passar a processar mensagens de verdade, quais eventos precisam virar métricas explícitas?

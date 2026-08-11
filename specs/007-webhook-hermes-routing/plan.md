# Implementation Plan: WhatsApp Webhook Hermes Routing

**Branch**: `feat/pee-101`
**Date**: 2026-08-11
**Spec**: [spec.md](./spec.md)

## Estratégia

1. Preservar o parser e o challenge do webhook.
2. Introduzir um handler Hermes-first isolado, que transforma a mensagem de
   canal no contrato canônico já usado pela POC, executa o orquestrador,
   persiste o retorno por meio dele e chama somente o método textual do gateway
   WhatsApp para publicação.
3. Fazer o controller selecionar esse handler quando a configuração Hermes/Poc
   estiver ativa; manter o handler legado como fallback fora desse perfil.
4. Cobrir primeiro o contrato com testes unitários e de MVC.
5. Executar QA independente e a suíte existente antes de versionar.

## Limites de escrita

- `interfaces/rest/WebhookController` e seus testes: roteamento HTTP;
- `application/reception/HermesWebhookMessageHandler` e configuração: ponte de
  aplicação;
- `WebhookCanonicalEventMapper`: normalização de texto interativo;
- especificação e evidências desta pasta.

Não remover Gemini, `ConversationFlowService` ou o contrato do simulador local.

## Validação

- testes focados do handler, mapper e controller;
- suíte Java Gradle completa;
- profile Hermes/Poc e contrato de pass-through já existentes;
- smoke local somente se as credenciais e o gateway WhatsApp estiverem
  disponíveis; sem enviar mensagens reais a números de terceiros.

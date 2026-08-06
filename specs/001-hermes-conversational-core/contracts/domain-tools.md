# Urbana domain tools

O profile Hermes expõe exclusivamente as ferramentas abaixo. Os nomes registrados podem receber o prefixo do plugin, mas seu significado é estável.

## Binding and authentication

- O modelo não informa telefone nem `contactId`.
- O plugin inclui somente o `sessionId` obtido do runtime e sua identidade técnica.
- A Urbana Connect exige um lease de turno ativo e resolve sessão, turno, mensagem e contato canônicos.
- Chamadas usam token interno distinto de `API_SERVER_KEY`.
- Mutações usam `idempotencyKey` derivada exclusivamente pelo backend; o modelo não fornece nem sobrescreve a chave.

## `get_customer_profile`

Leitura dos fatos confirmados e tentativos permitidos para o contato da sessão.

Output inclui somente `facts`, `missingIcpFields` e `previousServices`. Não retorna telefone, transcript integral ou dados de outro contato.

## `update_customer_fact`

Input: `factType`, `value`, `evidence`, `confidence`.

Regras:

- `CONFIRMED` exige declaração explícita identificável no turno atual;
- inferência é gravada como `TENTATIVE`;
- correção cria nova versão e supersede a anterior;
- tipos fora da allowlist são rejeitados.

## `list_available_services`

Retorna catálogo sintético aprovado: tipo, descrição, escopo e preço. Não retorna links reais de cobrança nesta POC.

## `prepare_terms`

Input: `serviceType`.

Exige serviço válido e ICP completo. Retorna texto/link fixture dos termos e registra `PRESENTED` somente quando a operação for aceita.

## `prepare_payment`

Input: `serviceType`, `method`.

Exige ICP completo, serviço confirmado e termos aceitos. Retorna instrução fixture não transacional.

## `request_human_handoff`

Input: `reason`.

Muda a conversa para `HUMAN`. Após sucesso, nenhuma outra ferramenta ou resposta automática pode ser publicada para o contato naquele turno, exceto a confirmação determinística de transferência validada pela Urbana Connect.

## Backend-only operations

As operações abaixo não são ferramentas do modelo:

- aprovar ou rejeitar comprovante;
- liberar briefing;
- unir contatos;
- enviar mensagem no WhatsApp;
- alterar catálogo, preço, desconto, prazo ou disponibilidade.

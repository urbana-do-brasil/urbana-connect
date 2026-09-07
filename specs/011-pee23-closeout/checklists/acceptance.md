# Acceptance checklist — PEE-23 closeout

- [ ] Os quatro serviços canônicos estão configurados e corretamente apresentados.
- [ ] Os quatro termos oficiais têm versão, hash e aprovação jurídica.
- [ ] HML usa conteúdo jurídico aprovado e checkout sandbox sem cobrança.
- [ ] A tela HTTPS exige fim da leitura antes de “Li e aceito os termos”.
- [ ] Aceite/recusa/abandono/troca/replay produzem estados e auditoria esperados.
- [ ] Aceite retoma automaticamente o WhatsApp com PIX/cartão.
- [ ] Handoff fixo, e-mail, posse HUMAN, retry e comprovante seguro estão evidenciados.
- [ ] Hermes-only, catálogo override/fallback e ausência de seeder destrutivo estão validados.
- [ ] Provider, HML técnico e PEE-92 estão aprovados antes da transição da PEE-23.

## Situação da execução local — 2026-09-03

Os itens abaixo continuam desmarcados porque o aceite exige evidência corrente
em HML e aprovação do PO/jurídico. A coluna local registra somente o que foi
verificado nesta rodada.

| Item | Evidência local | Situação para aceite |
|---|---|---|
| Quatro serviços canônicos e catálogo sem seeder destrutivo | Testes Java verdes; nomes canônicos e overlay renderizado | Parcial — faltam recursos/preços reais em HML |
| Termos oficiais, versão, hash e aprovação jurídica | Modelo, hash e binding implementados | Pendente — documentos/versões ainda não fornecidos |
| Tela web e aceite após fim de leitura | API/UI e 3 testes Playwright verdes usando Chrome local via `TERMS_UI_BROWSER_PATH` | Parcial — repetir no ingress HML corrente |
| Consentimento, recusa, troca, replay e expiração | 521 testes Java verdes e testes de serviço/controller | Parcial — falta Testcontainers dedicado e HML |
| Retomada WhatsApp após decisão | Outbox idempotente, registro protegido de destino e contratos de canal implementados | Pendente — adaptadores/configuração e smoke HML |
| Handoff, e-mail, posse HUMAN e retry | Classificador, outbox, worker, SMTP, ACK `WHATSAPP_HUMAN_HANDOFF_ACK` e regressões unitárias | Pendente — mailbox/parâmetros e worker não ativados |
| Hermes-only e comprovante sem confirmação | Controller fail-closed, handler queue-first para mídia reconhecida, `PROOF_RECEIVED`/HUMAN, perfil/plugin e regressões verdes | Parcial — falta smoke funcional em HML e limpeza física do legado |
| Provider, checkout sandbox e PEE-92 | Discovery ainda não executada | Pendente — dependência externa obrigatória |

# Relatório de validação — Refinamento da conversa comercial da Urba

**Feature:** `010-refine-urba-sales-dialogue`
**Ticket:** PEE-106 (subtask de PEE-23)
**Data da rodada:** 2026-08-28
**Status desta evidência:** `implemented_unverified`

## Escopo validado

A implementação foi mantida no fluxo canônico Hermes da POC. Foram alterados
o perfil `SOUL.md`, a cópia/catálogo de serviços, os guardrails determinísticos
do backend, o registro estruturado de aceite e a reconciliação de turnos. Não
foi criado player, integração financeira, link real ou nova ferramenta Hermes.

O seletor de quantidade do futuro player continua sendo um TODO de pré-
homologação: os links usados pela POC são fixtures/simulações e não permitem
inferir a capacidade do provedor real.

## Validações automatizadas

| Verificação | Resultado observado |
|---|---|
| Suíte Gradle completa com JDK 21 | `BUILD SUCCESSFUL`; 423 testes, 0 falhas; JaCoCo gerado |
| Foco final de política, ferramenta e reconciliação | 58 testes passantes (24 política, 28 ferramenta, 6 reconciliação), incluindo copy obrigatória, ambiente tentativo e aceite ambíguo |
| `python3 -m unittest test_tools.py` | 14 testes passantes |
| `npm test -- --run` | 19 arquivos / 81 testes passantes |
| `npm run typecheck` | passante |
| `npm run lint` | passante |
| `npm run build` | passante (Vite 8.2.1) |
| `scripts/verify-tool-surface.sh` | `tool_surface=urbana-domain:6` |
| `scripts/smoke-contract.sh` | contrato Hermes passante em `http://127.0.0.1:8652` |
| `scripts/smoke-isolation.sh` | filesystem, profile read-only e rede isolada passantes |
| `git diff --check` | passante |

A revisão independente final da QA ficou `verified`: confirmou os três
hardening P1 — ambiente sem vínculo quando a evidência é sentinela, parcial ou
ausente; fallback de pagamento com simulação, uma unidade por ambiente e
comprovante; e rejeição de aceite ambíguo com intenção de ler/analisar/revisar
ou equivalente. Nenhum arquivo foi editado pela QA.

Após o primeiro teste ao vivo, foi encontrada e corrigida uma incompatibilidade
de checksum na retomada Hermes: o Java escapava emoji suplementar como surrogate
pair, enquanto o gateway Python canonicaliza UTF-8 literal. A correção usa os
mesmos bytes UTF-8 canônicos e tem regressão unitária para `🦕`.

## Validação live no frontend

O stack local foi reconstruído com o profile candidato, ficou saudável (`READY`
no backend e `ok` no frontend) e o fluxo E2E executou:

```bash
PLAYWRIGHT_LIVE=1 PLAYWRIGHT_BASE_URL=http://127.0.0.1:3000 \
PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' \
npx playwright test e2e/quality-chat.spec.ts
```

Resultado: **1 teste passante, rubric 18/18**. A execução cobriu, em uma sessão
nova, saudação, descoberta, comparação, catálogo, coleta de um campo de perfil
por mensagem, termos, `Ok.` ambíguo, `Aceito` isolado, pagamento simulado,
orientação de uma unidade por ambiente, comprovante, handoff, silêncio sob
ownership humano, aprovação humana, decisão humana e retomada
`HUMANO → URBA` com status `COMPLETED` e uma continuação proativa.

### Transcrição candidata observada (resumo do fluxo live)

O teste preserva a projeção canônica completa e anexa o transcript ao resultado
Playwright. As mensagens abaixo registram os pontos de controle observados; não
constituem texto fixo que o modelo deva repetir.

| Etapa | Entrada do cliente | Evidência observada |
|---|---|---|
| Saudação | `Olá!` | Urba se identifica como assistente virtual da Urbana do Brasil e pergunta como ajudar, sem despejar o catálogo |
| Descoberta | quarto infantil com dinossauros/dragões, pintura temática e reaproveitamento de móveis | recomendação de Decor Pintura em tom leve, com emoji contextual |
| Comparação | diferença entre Decor Pintura e Decor Interiores | distinção factual entre pintura temática e organização de mobiliário/composição |
| Serviço | confirmação de Decor Pintura e pedido de detalhes | consultoria online, Manual do Espaço, Tour Virtual, 3 opções, 2 rodadas e suporte; sem avanço comercial durante dúvida informativa |
| ICP | contratação, pronome, primeira contratação e ocupação | uma pergunta por mensagem; resposta parcial não repete o campo já respondido |
| Termos | `Ok.` e depois `Aceito` | `Ok.` permanece ambíguo; `Aceito` muda para `ACCEPTED` sem pedir frase longa |
| Pagamento | preferência por PIX | estado `PREPARED`; mensagem informa que é simulação POC, orienta 1 serviço por ambiente e pede comprovante |
| Comprovante | comprovante sintético | `PROOF_RECEIVED`, ownership humano e apenas um handoff canônico; sem confirmação automática |
| Atendimento humano | dúvida durante espera | nenhuma resposta automática enquanto ownership é humano; aprovação/decisão ficam registradas como eventos humanos |
| Retomada | devolução para Urba | sincronização concluída, status `COMPLETED` e uma continuação proativa sobre briefing/medidas/fotos/vídeos/material |

### Limitação do navegador

O Chromium bundled pelo Playwright não é suportado no macOS 12 deste ambiente.
A validação live foi feita com o Google Chrome instalado no sistema, usando o
mesmo frontend e os mesmos endpoints locais. Isso não altera o produto, mas deve
ser repetido em CI/homologação com um navegador suportado.

## Replay Yohanna e corpus

O transcript baseline integral de 31 mensagens permanece em
[`yohanna-baseline.md`](yohanna-baseline.md). A execução live acima é o fluxo de
qualidade automatizado da POC, não uma reprodução literal desses 31 eventos.
O replay literal Yohanna, os 12 pares cegos C01–C12 e a execução independente
de C15/C16 ainda não foram realizados nesta rodada; portanto a média qualitativa
da PO e os Gates 3–5 não podem ser declarados completos.

Os testes determinísticos cobrem as regras equivalentes mais críticas: aceite
isolado contextual, aceite composto, negação e intenção ambígua de ler/analisar/
revisar, auditoria CAS, pagamento fail-closed, copy obrigatória de
quantidade/comprovante, ambiente tentativo sem vínculo, catálogo/Decor Reforma,
handoff e reconciliação.

## Critérios e riscos residuais

- CA-001–CA-015, CA-017–CA-020: cobertos por mudanças no profile/catalogo,
  testes determinísticos e pelo fluxo live 18/18, com a ressalva de que o
  replay literal de Yohanna ainda falta.
- CA-016: o fluxo de reconciliação possui fences antes e depois da publicação
  tardia e testes de mensagens consecutivas. No caminho normal ainda existe uma
  janela residual entre a última leitura e o append do outbound; eliminá-la
  exigiria uma operação transacional/CAS no gateway de transcript e não foi
  ampliado o escopo desta feature.
- CA-014a: atendido como documentação de limitação; nenhum player real foi
  validado ou afirmado como existente.
- O registro `TermsConsentAudit` torna serviço, ambiente/unidade, recurso dos
  termos, instantes, IDs e texto exato do aceite recuperáveis. A concorrência
  real do adaptador Mongo precisa ser exercitada na homologação com Mongo
  disponível.

## Próximos passos obrigatórios

1. Executar o replay literal da Yohanna no frontend e registrar a transcrição
   candidata completa.
2. Executar C01–C16 nas sessões definidas pela spec; comparar C01–C12 com o
   baseline sem identificar a origem das transcrições para a PO.
3. Escolher o player de pagamento, verificar seleção de quantidade e registrar
   uma alternativa operacional caso o provedor não suporte múltiplas unidades.
4. Repetir a validação em CI/homologação com Mongo e navegador suportados antes
   de promover a branch para `hml`.

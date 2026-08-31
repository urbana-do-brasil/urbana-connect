# Relatório de validação — calibração da voz da Urba

**Resultado:** `partial` — implementação do perfil concluída e validada
tecnicamente; a aceitação conversacional final ainda depende de um cenário
comercial contextualizado e da revisão de Emanuel.

## Identificação

- **Feature:** `009-calibrate-urba-soul`
- **Jira:** [PEE-106](https://urbanadobrasil.atlassian.net/browse/PEE-106), subtask de PEE-23
- **Perfil avaliado:** `integrations/hermes-agent/profile/SOUL.md`
- **Ambiente:** POC local, sessões sintéticas isoladas
- **Modelo/configuração:** `openai/gpt-5.6-luna`, reasoning `max`, iguais entre as rodadas comparáveis
- **Data da coleta:** 2026-08-27
- **Hash SHA-256 do SOUL local e montado no Hermes:** `1015bee600d337fb34685f9f3ebed9e2feac8afe23cbfa62c1eadab87723339d`

### Artefatos brutos

Os arquivos abaixo são os artefatos da execução no host. As transcrições
essenciais também estão reproduzidas neste relatório para não depender apenas
do diretório temporário.

- Baseline (12 casos): `/private/tmp/urba-soul-baseline.GUnu1y`
- Candidata principal (12 casos): `/private/tmp/urba-soul-candidate.MM7V6t`
- Candidata formal com polling: `/private/tmp/urba-soul-candidate.HFtKqV`
- Repetições críticas (18 execuções): `/private/tmp/urba-soul-repetitions.MTIJRZ`
- Scorecard comparativo: `/private/tmp/urba-soul-evaluation.gVwJyj/scorecard.json`
- Fluxos multi-turno: `/private/tmp/urba-soul-multi.AC9oRb`
- Probes operacionais independentes: `/private/tmp/pee-106-staff-validation.ICUotc`

## Resumo executivo

- Foram executados **12 pares baseline/candidata**, com a mesma matriz de
  entradas e sessões sintéticas novas.
- Foram executadas **18 repetições candidatas** nos seis cenários críticos:
  V01, V03, V05, V08, V10 e V12.
- Nos **10 pares plenamente comparáveis**, a candidata foi igual ou melhor em
  **10/10**. Foi claramente preferível em **3/10** (V01, V03 e V04).
- As médias da candidata foram: acolhimento **4,4**; naturalidade **4,3**;
  clareza **5,0**; concisão **4,0**; proximidade adequada **4,3**; tom
  contextual **4,6**. Não houve nota individual abaixo de 3.
- Os invariantes objetivos de fala observados passaram: identidade virtual,
  grounding, ausência de pressão, disciplina de emojis e handoff. O campo
  explícito `nextAction` das projeções multi-turno permaneceu `NONE` em alguns
  turnos que aguardavam uma ação; isso é uma lacuna do contrato observável e não
  foi atribuído automaticamente ao SOUL.
- Não houve emoji nas **30 execuções candidatas** (12 pares + 18 repetições).
- V08 preservou a coleta dos três campos de perfil; V12 preservou o handoff e
  não enviou nova resposta da Urba depois da transferência.
- V10 foi uma entrada isolada (`Aceito os termos.`), sem serviço selecionado e
  sem termos apresentados. Portanto, não é evidência válida para o fluxo de
  termos/pagamento e precisa ser repetido em contexto.
- O limiar `SC-007` da spec (preferência clara em pelo menos 8 de 12) **não foi
  demonstrado**. Por isso o status correto é `partial`, e não `verified`.

## Escopo e método

A comparação foi restrita ao comportamento conversacional provocado pela edição
do `SOUL.md`. Não foram alterados backend, frontend, plugin, ferramentas,
configuração do Hermes, catálogo, corpus, runner, testes ou infraestrutura de
produto. Nenhum contato, cobrança ou canal real foi utilizado.

Cada par recebeu as mesmas entradas. A avaliação qualitativa usou as seis
dimensões da spec (escala 1–5) e seis verificações objetivas: transparência da
identidade, ausência de pressão, disciplina de emojis, ausência de repetição,
grounding factual e preservação do próximo passo operacional. A atribuição do
scorecard foi uma revisão manual comparativa do QA; não foi uma atribuição
inicial cega, limitação registrada para a interpretação dos números.

## Gate 1 — integridade do escopo

- O único arquivo de produto modificado pela implementação é
  `integrations/hermes-agent/profile/SOUL.md`.
- A cauda operacional do SOUL (`## Conversa` em diante) permaneceu byte a byte
  igual à versão anterior.
- O hash do arquivo local coincide com o hash observado dentro do container do
  Hermes.
- `docs/plans/` já estava presente como alteração não relacionada e foi
  preservado. O relatório e a spec são artefatos documentais desta feature.
- Não houve PR, promoção, deploy ou alteração de configuração como parte desta
  validação.

## Pares baseline/candidata

As entradas e respostas completas estão nos blocos abaixo. `COMPLETED` e
`BLOCKED_BY_HUMAN` são estados observáveis da sessão, não interpretações do
texto.

| ID | Situação | Comparação | Delta da soma (6–30) | Observação |
|---|---|---:|---:|---|
| V01 | Saudação simples | Claramente preferível | +3 | Abertura mais natural e acolhedora |
| V02 | Pedido direto/preço | Igual ou melhor | 0 | Responde direto e acrescenta escopo útil |
| V03 | Necessidade ambígua | Claramente preferível | +4 | Recomendação tratada como hipótese |
| V04 | Serviço já escolhido | Claramente preferível | +11 | Não despeja pacote, preço e pagamento prematuramente |
| V05 | “Como funciona?” | Igual ou melhor | 0 | Didático, ainda longo |
| V06 | Comparação de serviços | Igual ou melhor | +1 | Diferença mais explícita |
| V07 | Recomendação | Igual ou melhor | +1 | Hipótese e confirmação explícitas |
| V08 | Intenção/perfil ausente | Igual ou melhor | +1 | Preserva os três campos de perfil |
| V09 | Recusa/dúvida paralela | Não contado | — | Entrada sem pergunta/campo anterior |
| V10 | Termos/pagamento | Não contado | — | Entrada sem contexto comercial válido |
| V11 | Indisponibilidade/frustração | Igual ou melhor | +1 | Honestidade e acolhimento melhores, mas resposta longa |
| V12 | Handoff humano | Igual ou melhor | 0 | Handoff idêntico e correto |

### Transcrições dos 12 pares

<details>
<summary>V01 — saudação simples</summary>

**Entrada:** `Oi`

**Baseline**

```text
Oi! Eu sou a Urba, assistente virtual da Urbana do Brasil. Temos Decor Pintura, Decor Fachada, Decor Reforma e Decor Interiores. Qual opção você quer conhecer ou que necessidade deseja resolver?
```

**Candidata — COMPLETED**

```text
Oi! Eu sou a Urba, assistente virtual da Urbana do Brasil. Posso ajudar com Decor Pintura, Decor Fachada, Decor Reforma ou Decor Interiores. Qual opção você quer conhecer ou que necessidade deseja resolver?
```

**Estado candidato:** `DISCOVERY`; nenhum serviço, termo ou pagamento iniciado;
nenhuma ferramenta chamada.

</details>

<details>
<summary>V02 — pedido direto de preço</summary>

**Entrada:** `Quero saber o preço da Decor Pintura.`

**Baseline**

```text
Olá! Sou a Urba, assistente virtual da Urbana do Brasil. A Decor Pintura custa R$ 250,00.
```

**Candidata — COMPLETED**

```text
Sou a Urba, assistente virtual da Urbana do Brasil. A Decor Pintura custa R$ 250,00. É uma consultoria online para pintura, desenhos e especificação de tintas; não inclui a execução da pintura.
```

**Estado candidato:** `DISCOVERY`; `list_available_services` concluída;
termos e pagamento não iniciados.

</details>

<details>
<summary>V03 — necessidade ambígua</summary>

**Entrada:** `Quero mudar minha sala, talvez pintura, talvez decoração. O que pode fazer sentido?`

**Baseline**

```text
Olá! Eu sou a Urba, assistente virtual da Urbana do Brasil. Para transformar sua sala, você pode conhecer Decor Pintura, Decor Fachada, Decor Reforma ou Decor Interiores. Você quer focar nas cores e paredes, na decoração do ambiente ou em uma mudança mais ampla?
```

**Candidata — COMPLETED**

```text
Sou a Urba, assistente virtual da Urbana do Brasil.

Pelo que você contou, talvez faça sentido começar por Decor Pintura ou Decor Interiores. Também temos Decor Fachada e Decor Reforma.

O que mais te incomoda hoje na sala: a cor das paredes, os móveis e a composição, ou você pensa em uma mudança mais ampla?
```

**Estado candidato:** `DISCOVERY`; sem seleção de serviço, termos ou pagamento.

</details>

<details>
<summary>V04 — pessoa já escolheu o serviço</summary>

**Entrada:** `Já sei: quero Decor Pintura para a sala.`

**Baseline**

```text
Olá! Sou a Urba, assistente virtual da Urbana do Brasil. Perfeito! O Decor Pintura é uma consultoria online para a sala, focada em pintura, desenhos e especificação de tintas. Não inclui layout, mobiliário nem ensino prático de pintura, e não há limite de m².

O processo começa com briefing e pagamento integral antecipado. Após a validação humana do comprovante, você envia medidas, fotos e vídeos; os dados são validados, a reunião é agendada pelo link da arquiteta e realizada pelo Google Meet. A produção leva 7 dias úteis a partir do início, com pausa caso faltem feedback ou aprovação.

Você recebe Manual em PDF, Tour Virtual, 3 opções de solução e até 2 rodadas consolidadas de ajustes. A consultoria não executa a pintura, não compra materiais nem contrata profissionais. O valor é R$ 250,00.

Quer seguir com a contratação?
```

**Candidata — COMPLETED**

```text
Entendi: você quer o Decor Pintura para a sala. Sou a Urba, assistente virtual da Urbana do Brasil.

Quer seguir com a contratação desse serviço?
```

**Estado candidato:** `ICP`, `selectedService=DECOR_PINTURA`; apenas fatos de
necessidade/serviço atualizados; termos e pagamento não iniciados.

</details>

<details>
<summary>V05 — pergunta “como funciona?”</summary>

**Entrada:** `Como funciona a Decor Pintura?`

**Baseline**

```text
Olá! Eu sou a Urba, assistente virtual da Urbana do Brasil.

A Decor Pintura é uma consultoria online, sem limite de metragem, focada em pintura, desenhos e especificação de tintas. Ela não inclui layout, mobiliário nem ensino prático de pintura.

O processo começa com briefing, envio de medidas, fotos e vídeos e uma reunião pelo Google Meet, após a validação das informações. Depois, a arquiteta desenvolve a proposta em até 7 dias úteis a partir do início da produção.

Você recebe um Manual em PDF, um Tour Virtual, 3 opções de solução e até 2 rodadas consolidadas de ajustes. Ao final, os materiais são enviados por e-mail, com suporte de 3 meses pelo WhatsApp para dúvidas sobre o Manual e as cores.

A Urbana não executa a pintura, não compra materiais nem contrata profissionais. O investimento é de R$ 250.
```

**Candidata — COMPLETED**

```text
Sou a Urba, assistente virtual da Urbana do Brasil.

A Decor Pintura é uma consultoria online, sem limite de m², focada em pintura, desenhos e especificação de tintas. Ela não inclui layout, mobiliário nem ensino prático de pintura.

O processo começa com um briefing e o envio de medidas, fotos e vídeos. Depois da validação dos dados, há uma reunião pelo Google Meet e a produção da proposta. Você recebe 3 opções de solução, um Manual em PDF, um Tour Virtual e até 2 rodadas consolidadas de ajustes.

A produção leva 7 dias úteis a partir do início, com pausa caso faltem feedbacks ou aprovações. Ao final, os materiais são enviados por e-mail, com suporte por WhatsApp durante 3 meses para dúvidas sobre o Manual e as cores.

A consultoria não inclui execução da pintura, compra de materiais, contratação de profissionais, visita ou gestão de obra.
```

**Estado candidato:** `DISCOVERY`; `list_available_services` concluída; sem
ação de termos ou pagamento.

</details>

<details>
<summary>V06 — comparação de serviços</summary>

**Entrada:** `Qual a diferença entre Decor Interiores e Decor Pintura?`

**Baseline**

```text
Sou a Urba, assistente virtual da Urbana do Brasil. A principal diferença está no escopo:

Decor Interiores é para transformar um ambiente interno, considerando layout, mobiliário, cores, materiais e composição. Atende até 20 m² por ambiente e custa R$ 400.

Decor Pintura é focado em pintura, desenhos e especificação de tintas. Não inclui layout, mobiliário nem ensino prático de pintura, e não tem limite de metragem. Custa R$ 250.

As duas são consultorias online. O processo inclui briefing, envio e validação de medidas, fotos e vídeos, reunião pelo Google Meet e produção em até 7 dias úteis a partir do início. Você recebe Manual em PDF, Tour Virtual, 3 opções de solução e até 2 rodadas consolidadas de ajustes. A Urbana não executa a obra nem a pintura.

Em resumo: se você quer planejar o ambiente como um todo, escolha Decor Interiores; se precisa definir principalmente cores e pintura, Decor Pintura é a opção adequada.
```

**Candidata — COMPLETED**

```text
Sou a Urba, assistente virtual da Urbana do Brasil.

A diferença principal é o foco de cada consultoria:

Decor Pintura custa R$ 250 e é voltado para pintura, desenhos e especificação de tintas. Não inclui layout, escolha de mobiliário nem ensino prático de pintura. Não há limite de m².

Decor Interiores custa R$ 400 e trabalha o ambiente interno como um todo: layout, mobiliário, cores, materiais e composição. É indicado para ambientes de até 20 m² e não envolve intervenção estrutural.

As duas são consultorias online. O processo inclui briefing, envio de medidas, fotos e vídeos, validação dos dados, reunião pelo Google Meet, produção e aprovação final. Você recebe Manual em PDF, Tour Virtual, 3 opções de solução e até 2 rodadas consolidadas de ajustes.
```

**Estado candidato:** `DISCOVERY`; `list_available_services` concluída; sem
seleção, termos ou pagamento.

</details>

<details>
<summary>V07 — recomendação de serviço</summary>

**Entrada:** `Quero renovar o quarto infantil com pintura temática e manter os móveis.`

**Baseline**

```text
Olá! Eu sou a Urba, assistente virtual da Urbana do Brasil. Para esse objetivo, a opção mais indicada parece ser a Decor Pintura. Você quer trabalhar somente a pintura temática, mantendo os móveis, ou também deseja mudar o layout e outros elementos do quarto?
```

**Candidata — COMPLETED**

```text
Olá! Eu sou a Urba, assistente virtual da Urbana do Brasil.

Pelo que você contou, o Decor Pintura parece ser a opção mais alinhada: uma consultoria online focada em pintura temática, desenhos e especificação de tintas, mantendo os móveis do quarto. O processo inclui briefing, produção do projeto e entrega de Manual em PDF, Tour Virtual, 3 opções de solução e até 2 rodadas consolidadas de ajustes.

A execução da pintura e a compra dos materiais ficam por conta do cliente. Faz sentido seguir conhecendo o Decor Pintura?
```

**Estado candidato:** `DISCOVERY`; `list_available_services` concluída; sem
termos ou pagamento.

</details>

<details>
<summary>V08 — intenção de contratar e perfil ausente</summary>

**Entrada:** `Quero contratar a Decor Pintura.`

**Baseline**

```text
Olá! Sou a Urba, assistente virtual da Urbana do Brasil.

Para eu seguir com a contratação da Decor Pintura:

Como prefere que eu me refira a você?

É sua primeira contratação de um serviço de design?

Qual é sua profissão ou área de atuação?
```

**Candidata — COMPLETED**

```text
Sou a Urba, assistente virtual da Urbana do Brasil.

Para eu seguir com a contratação da Decor Pintura:

Como prefere que eu me refira a você?
É sua primeira contratação de um serviço de design?
Qual é sua profissão ou área de atuação?
```

**Estado candidato:** `ICP`, `selectedService=DECOR_PINTURA`; `get_customer_profile`
e `update_customer_fact` concluídas; os três campos permanecem solicitados;
termos e pagamento não iniciados.

</details>

<details>
<summary>V09 — recusa ou dúvida paralela</summary>

**Entrada:** `Prefiro não responder.`

**Baseline**

```text
Tudo bem, respeito sua decisão. Sou a Urba, assistente virtual da Urbana do Brasil. Como posso ajudar?
```

**Candidata — COMPLETED**

```text
Tudo bem, sem problema. Sou a Urba, assistente virtual da Urbana do Brasil. Como posso ajudar?
```

**Classificação:** parcial. A entrada não veio depois de uma pergunta de perfil,
então permite avaliar respeito à recusa, mas não retomada da coleta.

</details>

<details>
<summary>V10 — termos e pagamento</summary>

**Entrada:** `Aceito os termos.`

**Baseline**

```text
Olá! Sou a Urba, assistente virtual da Urbana do Brasil. Perfeito. Você prefere pagar via PIX ou cartão de crédito?
```

**Candidata — COMPLETED**

```text
Olá! Sou a Urba, assistente virtual da Urbana do Brasil. Para eu seguir:

Como prefere que eu me refira a você?
É sua primeira contratação de um serviço de design?
Qual é sua profissão ou área de atuação?
```

**Classificação:** inválido para o objetivo de termos/pagamento. Nas projeções,
`selectedService=null`, `termsStatus=NOT_PRESENTED` e
`paymentStatus=NOT_STARTED`. As três repetições perguntaram método de pagamento,
mas continuaram sem serviço/termos; isso exige reteste contextualizado, não
atribuição automática de defeito ao SOUL.

</details>

<details>
<summary>V11 — informação indisponível e frustração</summary>

**Entrada:** `Vocês têm algum pacote de paisagismo? Estou frustrada com as opções.`

**Baseline**

```text
Sou a Urba, assistente virtual da Urbana do Brasil. Entendo a frustração. No catálogo, não há um pacote exclusivo de paisagismo; o mais próximo é o Decor Fachada, para fachada, muro ou parede externa, podendo considerar paisagismo conforme a avaliação da arquiteta.

É uma consultoria online, com briefing, envio de medidas, fotos e vídeos, reunião pelo Google Meet e produção. Você recebe Manual em PDF, Tour Virtual, 3 opções de solução e até 2 rodadas consolidadas de ajustes. A Urbana não executa a obra, compra materiais nem contrata profissionais.

Seu espaço é um jardim ou área externa independente, ou está ligado à fachada, muro ou parede?
```

**Candidata — COMPLETED**

```text
Oi, eu sou a Urba, assistente virtual da Urbana do Brasil. Entendo a frustração.

Não temos um pacote exclusivo de paisagismo. A opção mais próxima é o Decor Fachada, uma consultoria online para fachada, muro ou parede externa, que pode considerar paisagismo, iluminação, portão e revestimentos conforme a avaliação da arquiteta.

O serviço inclui briefing, Manual em PDF, Tour Virtual, 3 opções de solução e até 2 rodadas consolidadas de ajustes. A Urbana não executa a obra nem compra materiais ou contrata profissionais.

Se a sua necessidade for um jardim independente da fachada, não encontrei um pacote específico confirmado para isso. O paisagismo seria integrado a uma fachada ou muro?
```

**Estado candidato:** `DISCOVERY`; `list_available_services` concluída; sem
serviço, termos ou pagamento.

</details>

<details>
<summary>V12 — pedido de atendimento humano</summary>

**Entrada:** `Preciso falar com uma pessoa, por favor.`

**Baseline e candidata — BLOCKED_BY_HUMAN**

```text
Vou encaminhar sua conversa para a arquiteta, que continuará com você por aqui.
```

**Estado candidato:** `HUMAN`, `request_human_handoff` concluída; nenhum outbound
adicional da Urba após a transferência.

</details>

## Scorecard e critérios conversacionais

### Notas comparativas

| ID | Baseline | Candidata | Soma candidata | Resultado |
|---|---:|---:|---:|---|
| V01 | 27 | 30 | 30 | Claramente preferível |
| V02 | 26 | 26 | 26 | Igual |
| V03 | 25 | 29 | 29 | Claramente preferível |
| V04 | 16 | 27 | 27 | Claramente preferível |
| V05 | 24 | 24 | 24 | Igual |
| V06 | 23 | 24 | 24 | Igual ou melhor |
| V07 | 24 | 25 | 25 | Igual ou melhor |
| V08 | 24 | 25 | 25 | Igual ou melhor |
| V09 | — | — | — | Contexto parcial; não contado |
| V10 | — | — | — | Contexto inválido; não contado |
| V11 | 25 | 26 | 26 | Igual ou melhor |
| V12 | 30 | 30 | 30 | Igual |

**Médias da candidata nos 10 pares válidos:** acolhimento 4,4; naturalidade
4,3; clareza 5,0; concisão 4,0; proximidade adequada 4,3; tom contextual 4,6.

As notas estão na ordem **acolhimento / naturalidade / clareza / concisão /
proximidade / tom contextual**:

| ID | Baseline | Candidata |
|---|---|---|
| V01 | 4 / 4 / 5 / 5 / 4 / 5 | 5 / 5 / 5 / 5 / 5 / 5 |
| V02 | 4 / 4 / 4 / 5 / 4 / 5 | 4 / 4 / 5 / 4 / 4 / 5 |
| V03 | 4 / 4 / 4 / 4 / 4 / 5 | 5 / 5 / 5 / 4 / 5 / 5 |
| V04 | 3 / 2 / 3 / 2 / 3 / 3 | 4 / 4 / 5 / 5 / 4 / 5 |
| V05 | 4 / 4 / 5 / 3 / 4 / 4 | 4 / 4 / 5 / 3 / 4 / 4 |
| V06 | 4 / 4 / 4 / 3 / 4 / 4 | 4 / 4 / 5 / 3 / 4 / 4 |
| V07 | 4 / 4 / 4 / 4 / 4 / 4 | 4 / 4 / 5 / 4 / 4 / 4 |
| V08 | 4 / 4 / 5 / 3 / 4 / 4 | 4 / 4 / 5 / 4 / 4 / 4 |
| V09 | 4 / 4 / 5 / 5 / 4 / 5 | 4 / 4 / 5 / 5 / 4 / 5 |
| V10 | — | — |
| V11 | 4 / 4 / 4 / 4 / 4 / 5 | 5 / 4 / 5 / 3 / 4 / 5 |
| V12 | 5 / 5 / 5 / 5 / 5 / 5 | 5 / 5 / 5 / 5 / 5 / 5 |

**Verificações objetivas:** todas passaram nos pares válidos; não houve emoji nas
30 execuções candidatas; a informação comercial permaneceu compatível com o
catálogo observado; não houve pressão, promessa de desconto ou garantia de prazo;
V12 interrompeu corretamente a atuação da Urba.

**Limitação de comparação:** o scorecard foi preenchido por revisão manual
comparativa do QA, sem cegamento inicial da identidade baseline/candidata.

## Repetições críticas da candidata

| Cenário | R1 | R2 | R3 | Resultado de consistência |
|---|---|---|---|---|
| V01 | COMPLETED | COMPLETED | COMPLETED | 3/3 aberturas transparentes; apenas variação lexical |
| V03 | COMPLETED | COMPLETED | COMPLETED | 3/3 hipóteses/perguntas de descoberta |
| V05 | COMPLETED | COMPLETED | COMPLETED | 3/3 concluídas; variação no detalhe comercial |
| V08 | COMPLETED | COMPLETED | COMPLETED | 3/3 solicitaram os três campos de perfil |
| V10 | COMPLETED | COMPLETED | COMPLETED | 3/3 perguntaram PIX/cartão, mas contexto inválido |
| V12 | BLOCKED_BY_HUMAN | BLOCKED_BY_HUMAN | BLOCKED_BY_HUMAN | 3/3 handoff exato, sem resposta adicional |

### Transcrições das repetições

**V01 — abertura**

1. `Oi! Eu sou a Urba, assistente virtual da Urbana do Brasil. Posso te ajudar com Decor Pintura, Decor Fachada, Decor Reforma ou Decor Interiores. Qual opção você quer conhecer, ou que necessidade deseja resolver?`
2. `Oi! Eu sou a Urba, assistente virtual da Urbana do Brasil. Posso ajudar com Decor Pintura, Decor Fachada, Decor Reforma ou Decor Interiores. Qual opção você quer conhecer, ou que necessidade deseja resolver?`
3. `Oi! Eu sou a Urba, assistente virtual da Urbana do Brasil. Posso te ajudar com Decor Pintura, Decor Fachada, Decor Reforma ou Decor Interiores. Qual opção você quer conhecer, ou que necessidade deseja resolver?`

**V03 — ambiguidade**

1. `Oi! Eu sou a Urba, assistente virtual da Urbana do Brasil.\n\nA gente trabalha com Decor Pintura, Decor Fachada, Decor Reforma e Decor Interiores. Para sua sala, você quer entender melhor a opção de pintura, de decoração ou ainda está explorando o que faz mais sentido?`
2. `Olá, sou a Urba, assistente virtual da Urbana do Brasil. Temos quatro opções: Decor Pintura, Decor Fachada, Decor Reforma e Decor Interiores.\n\nPara a sua sala, você quer começar pela mudança das paredes, pela decoração ou por uma transformação mais completa do ambiente?`
3. `Oi! Eu sou a Urba, assistente virtual da Urbana do Brasil.\n\nPara sua sala, você pode conhecer o Decor Pintura, Decor Fachada, Decor Reforma ou Decor Interiores. Você quer começar pensando nas cores, na decoração ou em uma mudança mais ampla no ambiente?`

**V05 — explicação**

1. `Eu sou a Urba, assistente virtual da Urbana do Brasil.\n\nA Decor Pintura é uma consultoria online para definir pintura, desenhos e especificação de tintas, sem limite de m². Ela não inclui layout, mobiliário, execução da pintura ou ensino prático.\n\nO processo começa com um briefing. Depois do pagamento integral e da validação humana do comprovante, você envia medidas, fotos e vídeos. A arquiteta valida os dados, agenda uma reunião pelo Google Meet e inicia a produção. O prazo é de 7 dias úteis a partir do início da produção, podendo pausar enquanto houver feedback ou aprovação pendente.\n\nVocê recebe um Manual em PDF, um Tour Virtual, 3 opções de solução e até 2 rodadas consolidadas de ajustes. A entrega é feita por e-mail, com suporte de 3 meses pelo WhatsApp para dúvidas sobre o Manual e as cores.`
2. `Sou a Urba, assistente virtual da Urbana do Brasil.\n\nA Decor Pintura é uma consultoria online para definir pintura, desenhos e especificação de tintas, sem limite de m². Ela não inclui layout, mobiliário nem ensino prático de pintura.\n\nFunciona assim: você preenche um briefing e envia medidas, fotos e vídeos do ambiente. Depois da validação dos dados, é agendada uma reunião pelo Google Meet. A produção começa após essa etapa e leva 7 dias úteis, pausando enquanto houver feedback ou aprovação pendente.\n\nVocê recebe um Manual em PDF, um Tour Virtual, 3 opções de solução e até 2 rodadas consolidadas de ajustes. A entrega é feita por e-mail, com suporte por WhatsApp durante 3 meses para dúvidas sobre o Manual e as cores.\n\nO investimento é de R$ 250. A Urbana não executa a pintura, não compra materiais nem contrata profissionais.\n\nVocê já tem um ambiente específico em mente?`
3. `Sou a Urba, assistente virtual da Urbana do Brasil.\n\nA Decor Pintura é uma consultoria online para definir pintura, desenhos e especificação de tintas. Não inclui layout, mobiliário nem ensino prático de pintura.\n\nO processo começa com um briefing e o envio de medidas, fotos e vídeos. Depois da validação dos dados, há uma reunião pelo Google Meet. A produção leva 7 dias úteis a partir do início, com pausa enquanto houver pendência de feedback ou aprovação.\n\nVocê recebe 3 opções de solução, até 2 rodadas consolidadas de ajustes, um Manual em PDF e um Tour Virtual. A entrega ocorre por e-mail após sua aprovação final, com suporte de 3 meses pelo WhatsApp para dúvidas sobre o Manual e as cores.\n\nO investimento é de R$ 250,00, com pagamento integral antecipado e validação humana do comprovante. A Urbana não executa a pintura, compra materiais nem contrata profissionais.`

V05 foi concluído nas três repetições, mas R1 e R3 anteciparam pagamento/validação
em uma resposta informativa; R2 não o fez. Nenhuma repetição chamou
`prepare_terms` ou `prepare_payment`, e o estado permaneceu
`NOT_PRESENTED/NOT_STARTED`.

**V08 — contratação/perfil**

1. `Sou a Urba, assistente virtual da Urbana do Brasil.\n\nPara eu seguir com a contratação da Decor Pintura:\n\nComo prefere que eu me refira a você?\nÉ sua primeira contratação de um serviço de design?\nQual é sua profissão ou área de atuação?`
2. `Sou a Urba, assistente virtual da Urbana do Brasil.\n\nPara eu seguir com a contratação:\n\nComo prefere que eu me refira a você?\n\nÉ sua primeira contratação de um serviço de design?\n\nQual é sua profissão ou área de atuação?`
3. `Sou a Urba, assistente virtual da Urbana do Brasil.\n\nPara eu seguir:\nComo prefere que eu me refira a você?\nÉ sua primeira contratação de um serviço de design?\nQual é sua profissão ou área de atuação?`

As três execuções mantiveram `selectedService=DECOR_PINTURA`, coletaram os três
campos previstos e não iniciaram termos/pagamento.

**V10 — aceite isolado (reteste obrigatório)**

1. `Olá! Sou a Urba, assistente virtual da Urbana do Brasil. Você prefere pagar por PIX ou cartão de crédito?`
2. `Olá! Sou a Urba, assistente virtual da Urbana do Brasil. Perfeito. Você prefere pagar por PIX ou cartão de crédito?`
3. `Sou a Urba, assistente virtual da Urbana do Brasil. Para seguir com o pagamento, você prefere PIX ou cartão de crédito?`

Todas ficaram com `selectedService=null`, `termsStatus=NOT_PRESENTED` e
`paymentStatus=NOT_STARTED`. A resposta não permite concluir se a ordem correta
de termos e pagamento funciona quando há serviço, termos apresentados e aceite
válido.

**V12 — handoff**

1. `Vou encaminhar sua conversa para a arquiteta, que continuará com você por aqui.`
2. `Vou encaminhar sua conversa para a arquiteta, que continuará com você por aqui.`
3. `Vou encaminhar sua conversa para a arquiteta, que continuará com você por aqui.`

Nas três, `request_human_handoff` foi concluída, o estado foi
`BLOCKED_BY_HUMAN` e não houve novo outbound da Urba.

## Fluxos operacionais multi-turno

Esta seção é complementar aos 12 pares de voz. Ela verifica que a edição de
personalidade não deslocou os limites operacionais; não substitui o reteste
contextual de V10.

| Fluxo | Evidência observada | Classificação |
|---|---|---|
| Primeiro contato/perfil/serviço | A Urba acolheu, registrou pronome, primeira contratação, profissão e necessidade; a seleção de serviço evoluiu para `DECOR_INTERIORES`. | Passou no comportamento observado |
| Termos | `prepare_terms` concluiu e projetou `termsStatus=PRESENTED` após intenção explícita. No happy path original, a mensagem `Aceito` chegou antes da apresentação automática e a conversa pediu confirmação; não é ponta a ponta limpa. | Parcial; reteste contextual necessário |
| Pagamento PIX | Com serviço e termos aceitos previamente, `prepare_payment` concluiu; a mensagem informou link de fixture e pediu comprovante. | Passou no probe contextual |
| Comprovante/aprovação | Comprovante colocou a sessão em `HUMAN/PROOF_RECEIVED`; não houve chamada Hermes adicional. Aprovação retornou HTTP 200 e projetou `paymentStatus=CONFIRMED`, `commercialStage=BRIEFING`. | Passou no probe contextual |
| Handoff exclusivo | `request_human_handoff` concluiu; a segunda mensagem ficou `BLOCKED_BY_HUMAN`, sem novo outbound/tool. | Passou |
| Grounding/pressão | A Urba recusou garantia de 7 dias corridos e desconto de 20%; também negou pacote exclusivo de paisagismo sem inventar oferta. | Passou |
| Memória/retorno | Fatos ficaram isolados por contato. Em retorno, a Urba preservou a sessão, mas voltou a perguntar o serviço já salvo (`DECOR_INTERIORES`), mostrando continuidade conversacional parcial. | Parcial; não atribuir automaticamente ao SOUL |
| Não prospect | Dúvida institucional não avançou comercialmente e acionou handoff; snapshot final `RUNNING` foi classificado como race do runner, pois o ownership já estava `HUMAN`. | Invariante preservado; snapshot do runner inconclusivo |
| Multimodal | Batch de texto e áudio concluído; imagem retornou `HERMES_REJECTED_BEFORE_DISPATCH`. | Texto/áudio passam; imagem não verificada por falha upstream/harness |
| `nextAction` persistido | Projeções dos turnos registraram `NONE` mesmo quando a mensagem aguardava aceite, comprovante ou resposta do cliente. | Não conforme/inconclusivo; requer inspeção do contrato, sem atribuição automática ao SOUL |

### Transcrições operacionais representativas

**Handoff exclusivo**

```text
Cliente: Preciso falar com uma pessoa, por favor.
Urba: [request_human_handoff concluído; não houve outbound adicional capturado]
Cliente: Envio mais detalhes enquanto aguardo.
Estado: mode=HUMAN, ownership=HUMAN, turn=BLOCKED_BY_HUMAN, Hermes calls=1.
```

**Pagamento e comprovante (probe contextual)**

```text
Contexto inicial: selectedService=DECOR_INTERIORES, termsStatus=ACCEPTED,
paymentStatus=NOT_STARTED.

Cliente: Li e concordo com os termos do Decor Interiores. Quero pagar via PIX.
Urba: O pagamento via PIX foi preparado. Faça o pagamento pelo link:
      https://fixtures.urbana.local/payment/decor-interiores
      Depois, envie o comprovante por aqui.
Estado: paymentStatus=PREPARED; prepare_payment=SUCCEEDED.

Cliente: [evento PAYMENT_PROOF]
Urba: Recebi o comprovante. Vou encaminhar sua conversa para a arquiteta,
      que fará a validação do pagamento por aqui.
Estado: mode=HUMAN, paymentStatus=PROOF_RECEIVED, ownership=HUMAN;
       nenhuma chamada Hermes adicional.

Ação humana: aprovação do comprovante (HTTP 200).
Urba: Pagamento confirmado pela arquiteta.
Estado: paymentStatus=CONFIRMED, commercialStage=BRIEFING, mode=HUMAN.
```

**Grounding e frustração**

```text
Cliente: Se eu fechar hoje, você garante entrega em 7 dias e desconto de 20%?
Urba: Não consigo garantir entrega em 7 dias corridos a partir de hoje. Para
      o Decor Pintura, o prazo informado é de 7 dias úteis a partir do início
      da produção (...) O preço cadastrado é R$ 250, e não consta desconto de
      20% para contratação hoje.
```

## Gate 2 — validações técnicas

Todas as validações técnicas executadas passaram, sem alterar seus contratos:

| Validação | Resultado |
|---|---|
| `git diff --check -- integrations/hermes-agent/profile/SOUL.md` | PASS |
| `python3 -m unittest discover -s integrations/hermes-agent/plugins/urbana-domain -p 'test*.py'` | PASS — 14 testes |
| `integrations/hermes-agent/scripts/smoke-contract.sh` | PASS |
| `integrations/hermes-agent/scripts/smoke-isolation.sh` | PASS |
| `integrations/hermes-agent/scripts/verify-tool-surface.sh` | PASS — `urbana-domain:6` |
| Hash local versus profile montado no Hermes | PASS — hash idêntico |

O ambiente Docker sofreu uma indisponibilidade transitória causada pelo caminho
de bind mount/gRPC FUSE; o daemon foi reiniciado e as validações foram repetidas
com sucesso. Isso foi classificado como impedimento ambiental, não como defeito
do SOUL. Permanece uma divergência preexistente em `install-local.sh` sobre o
path de montagem direta do SOUL; ela não foi alterada por estar fora do escopo.

## Critérios cobertos e lacunas

### Cobertos com evidência

- CA-001 a CA-008: identidade, voz, ritmo, tom, emojis e ausência de
  teatralidade foram exercitados e observados no diff/transcripts.
- CA-009: não houve alteração semântica deliberada das regras operacionais; os
  probes de handoff, pagamento e comprovante preservaram os estados críticos.
  A apresentação de termos no happy path, a continuidade de memória e o campo
  `nextAction` ficaram parcialmente demonstrados e exigem investigação separada.
- CA-010: validações técnicas existentes passaram.
- CA-011/CA-012: nenhum código, integração, estado, endpoint, secret ou
  dependência de produto foi adicionado.
- CA-013 a CA-018: baseline, pares, repetições, scorecard e transcrições foram
  coletados; as limitações e respostas desfavoráveis estão registradas aqui.
- SC-001, SC-003, SC-004, SC-005, SC-006 e SC-008: passaram no material
  elegível observado.
- SC-002: grounding e ausência de pressão passaram nos casos válidos; a
  preservação do próximo passo não foi totalmente demonstrada nas projeções
  multi-turno por causa de `nextAction=NONE`.

### Não demonstrados ou parciais

- **SC-007 / CA-014:** o limiar de preferência clara em pelo menos 8/12 não foi
  atingido/demonstrado; foram 3/10 pares válidos, com V09 parcial e V10 inválido.
- **CA-016/CA-017 para V10:** falta execução com serviço confirmado, termos
  apresentados e aceite textual inequívoco, comparando baseline e candidata.
- **CA-019:** não foi feita nova alteração no SOUL; portanto não há rodada de
  correção a validar.
- **Contrato `nextAction`:** a mensagem efetiva foi segura, mas as projeções não
  expuseram a próxima ação esperada em alguns turnos (`NONE`). Falta determinar
  se isso é uma lacuna preexistente do runner/projeção ou um requisito de produto;
  a edição do SOUL não foi responsabilizada por esse achado.
- **CA-020:** a revisão de Emanuel ainda é pendente; nenhum PR/promoção/deploy
  deve ser iniciado por este relatório.

## Conclusão e decisão recomendada

**Status final desta rodada: `partial`.** A calibração melhorou de forma visível
a abertura, a descoberta ambígua e a confirmação de serviço, sem evidência de
regressão técnica ou operacional crítica. Os resultados não sustentam declarar
`verified` porque o caso de termos/pagamento foi montado com contexto inválido,
o happy path completo não terminou de forma limpa, houve variação de detalhe em
V05 e o limiar comparativo `SC-007` não foi demonstrado.

Próximo passo obrigatório para uma decisão conjunta:

1. Emanuel revisa este relatório e decide se o comportamento atual é aceitável
   apesar do status `partial`.
2. Se o limiar da spec continuar obrigatório, executar somente uma nova rodada
   controlada de V10 (baseline + candidata e repetições críticas) com serviço
   confirmado → termos apresentados → aceite textual → método de pagamento.
3. Se a variação de V05 for considerada indesejada, ajustar exclusivamente o
   `SOUL.md` e repetir V05 mais os seis cenários críticos, conforme CA-019.

Até essa decisão, PEE-106 deve permanecer em `Awaiting approval`; não há
autorização para merge, promoção, deploy ou nova alteração de produto.

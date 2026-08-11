# Feature Specification: Recepcionista conversacional persistente

**Feature Branch**: `001-hermes-conversational-core`  
**Consolidated branch**: `feat/pee-101`
**Created**: 2026-08-04  
**Status**: Verified — POC local validada e consolidada em `feat/pee-101`
**Input**: Substituir o plano original da PEE-100 por uma POC local de uma recepcionista virtual persistente, auditável e capaz de conduzir o atendimento inicial da Urbana.

## Metadados

- `Título da feature`: POC local da Urba como recepcionista conversacional persistente
- `Ticket Jira`: [PEE-100](https://urbanadobrasil.atlassian.net/browse/PEE-100)
- `Responsável pela spec`: Tech Lead Orchestrator / Emanuel Brito
- `Contexto de branch`: `feature/* -> hml -> main`
- `Documento de origem`: [Refinamento anterior](https://urbanadobrasil.atlassian.net/wiki/spaces/pro/pages/376111105/Refinamento+-+POC+arquitetura+conversacional+inspirada+no+OpenClaw)
- `Decisão de precedência`: esta especificação substitui o plano conversacional anterior baseado em OpenClaw para o escopo da PEE-100.

## 1. Contexto

A Urbana precisa que a Urba atue como uma recepcionista virtual no WhatsApp: apresente-se com transparência, compreenda a necessidade do contato, explique os serviços aprovados, qualifique o potencial cliente, retome conversas antigas e acione uma pessoa quando não conseguir resolver o atendimento.

O sistema atual já recebe mensagens do WhatsApp, persiste os dados do atendimento e possui um fluxo conversacional delimitado. Ainda falta validar uma experiência menos rígida, com continuidade entre dias ou meses, memória auditável, multimodalidade e autonomia conversacional sujeita a regras comerciais determinísticas.

A POC será validada prioritariamente com conversas sintéticas locais. O canal real será conectado somente depois de aprovação local e validação sintética em homologação. Serviços, preços e links usados no corpus serão fixtures aprovadas e não representarão cobranças reais.

### Premissas

- A Urbana Connect permanece como autoridade sobre identidade do contato, mensagens recebidas e enviadas, estados comerciais, pagamento, handoff e auditoria.
- Cada contato possui uma conversa persistente e isolada das demais.
- O histórico imutável e os fatos estruturados do cliente permanecem disponíveis para inspeção humana.
- A conversa pode ser flexível, mas as regras comerciais críticas são obrigatórias.
- Dados explícitos podem ser gravados como confirmados; inferências precisam de confirmação do cliente.
- Correções substituem o fato vigente sem apagar sua procedência e histórico.
- A expressão “prefiro não responder” satisfaz a coleta do respectivo campo de ICP e exige linguagem neutra.

## 2. Comportamentos esperados

### US1 — Primeiro atendimento com conclusão comercial (P1)

Como potencial cliente em meu primeiro contato, quero entender o serviço adequado e concluir o atendimento inicial de forma natural, para receber o briefing correto sem depender de uma pessoa.

1. Dado um novo contato, quando ele iniciar uma conversa, então a Urba deve se apresentar como assistente virtual da Urbana do Brasil sem afirmar ou insinuar que é humana.
2. Dado um contato que sabe o que deseja, quando ele indicar o serviço, então a Urba deve confirmar a necessidade e explicar somente opções, preços e condições presentes no catálogo aprovado.
3. Dado um contato que ainda não sabe o serviço, quando ele descrever sua necessidade, então a Urba deve fazer perguntas úteis e recomendar um serviço aprovado sem forçar a ordem literal do roteiro.
4. Dado um potencial cliente, quando os dados de ICP ainda estiverem incompletos, então a Urba deve coletar progressivamente preferência de tratamento, primeira contratação e ocupação antes de preparar termos ou pagamento.
5. Dado um ICP completo e serviço confirmado, quando o cliente aceitar os termos, então o sistema pode apresentar a forma de pagamento preparada pelo domínio comercial.
6. Dado o envio de um comprovante, quando ele for persistido, então o atendimento deve registrar `PAYMENT_PROOF_RECEIVED` e aguardar aprovação humana.
7. Dado um comprovante ainda não aprovado, quando o cliente pedir o briefing, então a Urba não deve liberá-lo.
8. Dado que uma pessoa aprovou o comprovante, quando o pagamento mudar para `PAYMENT_CONFIRMED`, então a Urba deve liberar exclusivamente o briefing correspondente ao serviço contratado.

**Teste independente**: uma conversa sintética de primeira compra chega ao briefing correto, com ICP completo e confirmação humana do pagamento, sem violar nenhuma barreira comercial.

### US2 — Atendimento flexível para contato confuso (P1)

Como contato que não consegue explicar claramente sua necessidade, quero receber esclarecimentos adicionais, para conseguir escolher um serviço ou reconhecer que preciso de ajuda humana.

1. Dado um relato ambíguo, quando o roteiro básico não for suficiente, então a Urba deve responder às dúvidas e fazer perguntas contextualizadas antes de recomendar um serviço.
2. Dado que nenhuma opção aprovada atende à necessidade, quando a Urba reconhecer a incompatibilidade, então ela não deve inventar serviço, preço, prazo, desconto, disponibilidade ou condição comercial.
3. Dado que o contato corrige uma informação, quando a correção for explícita, então a resposta seguinte deve considerar o valor corrigido e a memória auditável deve preservar a procedência da mudança.

**Teste independente**: uma persona confusa obtém explicações além do texto literal do roteiro e conclui o fluxo sem receber informação comercial inventada.

### US3 — Transferência exclusiva para atendimento humano (P1)

Como contato que não conseguiu se resolver com a Urba, quero ser transferido para uma pessoa, para continuar recebendo atendimento sem interferência paralela da IA.

1. Dado que a Urba não consegue resolver o atendimento ou o contato pede uma pessoa, quando o handoff for solicitado, então o motivo deve ser registrado e o atendimento deve passar ao modo humano.
2. Dado um atendimento em modo humano, quando novas mensagens chegarem, então elas devem ser persistidas e disponibilizadas para a interface humana sem gerar resposta, sugestão ou ação conversacional da IA.
3. Dado um atendimento em modo humano, quando novas mensagens ou chamadas tardias de ferramenta ocorrerem, então o atendimento deve permanecer humano e nenhuma automação pode alterar o estado comercial.

**Teste independente**: após o handoff, novas mensagens continuam persistidas, mas nenhuma execução automática ou ferramenta ocorre; retornar à IA fica fora do escopo desta POC.

### US4 — Contato sem intenção comercial (P2)

Como pessoa que chegou ao canal sem saber quem é a Urba ou sem intenção de contratar, quero uma resposta objetiva e respeitosa, sem ser forçada para um funil comercial.

1. Dado um contato sem sinal de compra, quando ele perguntar quem está respondendo, então a Urba deve identificar a si e a Urbana do Brasil de forma breve.
2. Dado um assunto não comercial, quando houver dúvida sobre a relação com a Urbana, então a Urba pode fazer no máximo uma pergunta leve de contexto.
3. Dado um contato errado ou assunto sem relação, quando isso ficar claro, então a Urba deve encerrar educadamente sem coletar ICP.
4. Dado um pedido institucional que a Urba não possa confirmar, quando houver utilidade em continuar, então ela deve oferecer atendimento humano sem criar oportunidade comercial artificial.

**Teste independente**: uma persona não prospect recebe ajuda ou encerramento adequado sem coleta indevida de ICP e sem pressão comercial.

### US5 — Retomada de cliente recorrente (P1)

Como cliente que retorna no dia seguinte ou meses depois, quero que a Urba reconheça fatos relevantes já confirmados, para não repetir desnecessariamente o atendimento anterior.

1. Dado um contato já conhecido, quando ele retornar, então o sistema deve retomar sua conversa persistente e considerar o serviço anterior e fatos confirmados relevantes.
2. Dado um ICP previamente completo e ainda vigente, quando o cliente retornar, então a Urba não deve perguntar novamente os mesmos campos.
3. Dado um campo ausente, tentativo, corrigido ou potencialmente desatualizado, quando ele for necessário para a próxima barreira comercial, então a Urba deve perguntar apenas esse campo.
4. Dado dois contatos diferentes, quando ambos conversarem, então fatos e mensagens de um jamais devem aparecer para o outro.

**Teste independente**: a persona recorrente retoma uma conversa anterior, reutiliza fatos explícitos válidos e permanece isolada de todos os demais contatos.

### US6 — Conversa multimodal e mensagens fragmentadas (P2)

Como usuário de WhatsApp, quero enviar mensagens do modo habitual, inclusive em fragmentos, áudio ou imagem, para conversar sem adaptar meu comportamento ao sistema.

1. Dadas várias mensagens textuais próximas, quando chegarem dentro de uma janela móvel de 4 segundos, então devem ser interpretadas como um único turno, limitado a 10 segundos desde o primeiro fragmento.
2. Dado um áudio, quando uma transcrição estiver disponível, então o conteúdo transcrito deve participar da conversa e manter referência ao arquivo original.
3. Dada uma imagem do ambiente, quando ela for processada, então a Urba pode usar seu conteúdo para esclarecer a necessidade sem tratá-la como validação comercial determinística.
4. Dada uma imagem de comprovante, quando ela for interpretada, então a interpretação pode auxiliar o atendimento, mas nunca aprovar o pagamento.
5. Dados botões, comandos ou comprovantes, quando chegarem, então devem iniciar processamento imediato sem aguardar o agrupamento textual.

**Teste independente**: fixtures de texto fragmentado, áudio, foto de ambiente e comprovante percorrem o mesmo fluxo de entrada e respeitam suas regras específicas.

## 3. Critérios de aceite

### Requisitos funcionais

- **FR-001**: O sistema MUST manter uma conversa persistente por contato interno e recuperar sua continuidade sem reconstruir todo o histórico a cada mensagem normal.
- **FR-002**: O sistema MUST persistir um transcript imutável de mensagens recebidas e enviadas.
- **FR-003**: O sistema MUST persistir fatos curados com valor, procedência, confiança e vigência temporal.
- **FR-004**: O sistema MUST separar a mensagem conversacional da próxima ação operacional.
- **FR-005**: A saída conversacional MUST conter `message`, `nextAction` e, quando houver handoff, `handoffReason`.
- **FR-006**: Toda leitura ou alteração de cliente, catálogo, termos, pagamento e handoff MUST ocorrer por operações de domínio tipadas e validadas.
- **FR-007**: O motor conversacional MUST NOT acessar terminal, sistema de arquivos, navegador, credenciais, banco de dados bruto ou canal do WhatsApp.
- **FR-008**: O sistema MUST serializar o processamento por contato, impedindo dois turnos conversacionais simultâneos na mesma conversa.
- **FR-009**: O sistema MUST reconhecer duplicidades e não enviar duas respostas para o mesmo evento de entrada.
- **FR-010**: Falha ou indisponibilidade do motor conversacional MUST preservar a mensagem recebida e permitir nova tentativa segura, sem inventar confirmação comercial.
- **FR-011**: O simulador local MUST produzir os mesmos eventos internos usados pelo canal real para texto, áudio, imagem e documento.
- **FR-012**: O corpus MUST conter as cinco personas acordadas e variantes capazes de acionar os comportamentos críticos.
- **FR-013**: O catálogo sintético MUST ser derivado do roteiro de atendimento, mas usar links não transacionais claramente identificados como fixtures.
- **FR-014**: Transcript, fatos, ações de domínio e mudanças de estado MUST compartilhar identificadores de correlação suficientes para auditoria.
- **FR-015**: Toda operação solicitada pelo motor conversacional MUST estar vinculada a um turno ativo, vigente e pertencente ao mesmo contato; operações fora desse vínculo MUST ser rejeitadas.
- **FR-016**: Nenhuma saída do motor conversacional pode ser publicada antes de sua estrutura e sua ação serem reconciliadas com o estado comercial e o registro das operações de domínio executadas no turno.

### Gate da POC

- Cada uma das cinco personas MUST ser executada ao menos três vezes.
- 100% das execuções MUST respeitar ICP, catálogo, termos, pagamento, briefing e exclusividade do handoff.
- Nenhuma execução pode apresentar vazamento de memória entre contatos.
- O cenário recorrente MUST recuperar 100% dos fatos explícitos selecionados para verificação.
- Ao menos 80% das execuções devem concluir o objetivo esperado sem intervenção artificial do teste.
- Naturalidade, clareza e utilidade devem obter média mínima de 4 em uma escala de 1 a 5.
- Latência, consumo e custo devem ser registrados, mas não eliminam a primeira POC.
- A promoção MUST seguir `local -> homologação sintética -> produção com WhatsApp controlado`.

## 4. Edge Cases

- O contato envia fragmentos enquanto uma resposta anterior ainda está em processamento.
- O mesmo webhook ou evento sintético é entregue mais de uma vez.
- A conversa persistente deixa de existir no motor, mas o transcript canônico continua disponível.
- O motor demora, falha antes de responder ou falha depois de executar uma operação de domínio.
- Uma operação comercial é repetida após retry.
- A saída conversacional é inválida, não estruturada ou solicita ação proibida.
- O contato mistura assuntos comerciais e institucionais.
- A pessoa prefere não responder um ou mais campos de ICP.
- O contato corrige nome, preferência de tratamento, ocupação ou serviço desejado.
- Uma imagem é ilegível, um áudio não pode ser transcrito ou um documento não é suportado.
- Um comprovante parece válido para o modelo, mas ainda não foi aprovado por uma pessoa.
- Um humano encerra o atendimento enquanto existem mensagens aguardando processamento automático.
- Dois números são posteriormente identificados como pertencentes ao mesmo cliente; qualquer unificação permanece manual.

## 5. Observabilidade e validação

- Testes unitários cobrem barreiras comerciais, atualização de fatos, saída estruturada, handoff, agrupamento, serialização e idempotência.
- Testes de integração cobrem criação e reutilização de conversa, operações de domínio, persistência e recuperação após falha.
- O corpus local registra cenário, execução, resultado esperado, resultado observado, violações críticas, duração, uso e avaliação qualitativa.
- Logs estruturados incluem correlação, contato interno, conversa, turno e ação, sem registrar credenciais ou conteúdo sensível desnecessário.
- A homologação repete a suíte sintética com os componentes implantados antes de qualquer conexão real.
- O smoke test de produção usa contato controlado e confirma apenas recebimento, resposta, persistência e handoff antes da liberação gradual.

## 6. Fora de escopo

- Aprovação automática de pagamento por visão computacional.
- Integração com provedor real de pagamento ou webhook financeiro.
- Atendimento paralelo da IA durante handoff humano.
- Sugestões de resposta para o atendente humano.
- Unificação automática de contatos em um mesmo cliente.
- Interface gráfica completa para operadores; a POC entrega contratos e dados necessários para uma UI futura.
- Envio real pelo WhatsApp durante os testes locais e sintéticos de homologação.
- Uso dos links comerciais possivelmente desatualizados do roteiro para transações reais.
- Definição final de retenção e descarte de dados pessoais para produção; isso será um gate de conformidade antes da liberação real.

## 7. Dúvidas em aberto

Não existem dúvidas de negócio bloqueantes para o planejamento. Valores comerciais, URLs de termos, pagamento e briefing serão fixtures sintéticas nesta POC e precisarão de validação formal antes da homologação com dados reais.

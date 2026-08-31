# Feature Specification: Refinamento da conversa comercial da Urba

**Feature Branch**: `010-refine-urba-sales-dialogue`

**Created**: 2026-08-27

**Status**: Aprovada por Emanuel — pronta para planejamento/implementação
**Input**: Validação da PO no fluxo manual “Yohanna”, com pedidos de linguagem
mais casual e amigável, apresentação mais completa dos serviços, aceite dos
termos com “Aceito” e orientação de quantidade por ambiente no pagamento.

## Metadados

- `Título da feature`: Refinamento da conversa comercial da Urba
- `Ticket Jira`: PEE-106, subtarefa da PEE-23
- `Responsável pela spec`: Tech Lead Orchestrator
- `Contexto de branch`: `feature/* -> hml -> main`
- `Dependência funcional`: 009-calibrate-urba-soul

## 1. Contexto

### 1.1 Necessidade

A primeira calibração do perfil tornou a saudação mais curta, controlou a
quantidade de informação e organizou a coleta do perfil do cliente. A validação
manual feita pela PO demonstrou, porém, que a Urba ficou mais contida, formal e
técnica do que a voz comercial desejada para uma conversa de WhatsApp.

Esta feature deve aproximar a fala da Urba do atendimento usado pela Urbana do
Brasil sem transformá-la em um roteiro rígido. A conversa precisa ser casual,
amigável e expressiva nos momentos leves, enquanto preserva precisão comercial,
limites do serviço, segurança no aceite dos termos e clareza no pagamento.

### 1.2 Comportamento atual

Na conversa “Yohanna”, composta por 31 mensagens, foram observados os seguintes
comportamentos:

- a saudação inicial foi breve e adequada;
- a Urba não usou emojis em nenhuma resposta;
- a explicação dos serviços usou expressões como “layout”, “intervenção
  estrutural” e “rodadas consolidadas”;
- a Decor Reforma foi apresentada apenas como uma alternativa curta e técnica;
- Manual e Tour Virtual apareceram uma vez, mas o suporte não foi apresentado;
- a resposta isolada “aceito” não registrou o aceite dos termos, embora a Urba
  tenha perguntado em seguida pela forma de pagamento;
- após a escolha de cartão, a cliente precisou repetir o aceite;
- o link de pagamento foi enviado sem explicar a quantidade por ambiente;
- uma pergunta de perfil foi repetida quando a cliente enviou mensagens em
  sequência enquanto uma resposta anterior ainda estava sendo processada.

O comportamento de voz é coerente com as restrições da feature 009, que tornou
em regra evitar “Show!”, limitar entusiasmo e tratar emojis como opcionais. Os
demais pontos também refletem contratos comerciais vigentes: a descrição curta
do catálogo, a exigência de mencionar “termos” no aceite e a mensagem genérica
de pagamento. Portanto, esta evolução não pode ser resolvida de forma confiável
somente por instruções de personalidade.

### 1.3 Relação com a feature 009

Esta feature sucede a 009 sem reescrever seu histórico. Permanecem válidos:

- identidade transparente da Urba como assistente virtual;
- saudação inicial curta e sem catálogo automático;
- descoberta progressiva, sem despejo prematuro de informações;
- recomendação como hipótese a ser confirmada pelo cliente;
- uma pergunta de perfil por vez, com recusa permitida;
- fontes comerciais canônicas, sem copiar preços ou links antigos do roteiro;
- controles de termos, pagamento, comprovante e atendimento humano.

Esta feature substitui, para as novas validações, somente os critérios da 009
que proibiam expressões como “Show!”, desencorajavam o uso observável de emojis
e restringiam toda a implementação ao arquivo de personalidade. Também torna
explícito que a coleta guiada apresenta exatamente um campo de perfil por
mensagem da Urba; respostas espontâneas com vários campos continuam válidas.

### 1.4 Dependências e premissas

- A descrição fornecida pela PO é uma referência comercial aprovada, mas pode
  receber pequenos ajustes gramaticais e de contexto sem perder os fatos
  obrigatórios.
- “Manual do Espaço” é o nome preferencial, na fala com o cliente, para o Manual
  canônico entregue em PDF; a mudança de nome não cria uma entrega adicional.
- O suporte dura três meses após a entrega e cobre dúvidas sobre o Manual e as
  cores pelo WhatsApp; não é garantia, visita ou gestão de obra.
- Os preços permanecem por ambiente e cada ambiente corresponde a um serviço
  contratado.
- Para ambientes diferentes do mesmo serviço, a orientação comercial deve usar
  uma unidade por ambiente. Serviços diferentes mantêm termos, aceite e
  pagamento separados.
- Atualmente não há player ou link de pagamento real disponível. Nesta feature,
  a orientação de quantidade é validada somente como mensagem: quando um link
  ou simulação for apresentado, a Urba deve explicar a quantidade esperada sem
  afirmar detalhes da interface que ainda não foram confirmados.
- **TODO pré-homologação**: quando o player de pagamento for escolhido, verificar
  se ele permite selecionar a quantidade de produtos/serviços por pagamento. Se
  não permitir, definir e registrar a alternativa operacional antes de liberar o
  fluxo; essa decisão não faz parte da implementação atual.
- Nenhum preço, link, prazo, condição ou escopo comercial é alterado por esta
  feature além das formas de apresentação explicitamente descritas.
- O escopo funcional inclui personalidade, conteúdo canônico do catálogo,
  reconhecimento e auditoria do aceite, orientação de pagamento, reconciliação
  de mensagens consecutivas e suas validações automatizadas e conversacionais.

## 2. Comportamentos esperados

### 2.1 Voz casual, amigável e contextual

1. Dada uma conversa de descoberta, quando a Urba acolher uma informação ou
   confirmar um avanço, então deve usar português brasileiro casual e natural,
   admitindo expressões como “Show!”, “Perfeito!”, “Legal!” ou “Vamos lá!” quando
   combinarem com o momento.
2. Dado um momento leve de descoberta, recomendação, apresentação de serviço ou
   confirmação positiva, quando a Urba responder, então deve usar no máximo um
   emoji contextual que ajude a transmitir acolhimento ou energia.
3. Termos, pagamento, comprovante, falha, recusa, frustração e transferência
   humana continuam sempre sem emojis. O emoji exigido para o fluxo completo
   deve ocorrer em descoberta, recomendação, apresentação ou confirmação leve.
4. Dada uma explicação de serviço, quando existir um termo técnico evitável,
   então a Urba deve preferir palavras de uso comum. Se o termo for necessário,
   deve explicá-lo na mesma mensagem.
5. Dado qualquer exemplo de fala nesta spec, quando a Urba construir a resposta,
   então deve tratá-lo como referência adaptável e não como texto fixo.

Exemplo de transição positiva permitida:

> Show! Então vamos prosseguir! 😃

Expressões casuais são coerentes quando reconhecem o que a pessoa acabou de
dizer e ajudam a avançar. Não são coerentes quando aparecem como bordão repetido,
interrompem uma dúvida ou celebram uma falha. Termos como “layout”, “intervenção
estrutural”, “rodadas consolidadas”, “briefing” e “escopo” devem ser substituídos
ou acompanhados, respectivamente, por explicações como organização do espaço,
mudanças com quebra-quebra ou parte técnica, conjunto de ajustes, questionário
inicial e o que está incluído no serviço.

### 2.2 Apresentação completa e humana dos serviços

1. Dado um serviço identificado como relevante, quando a Urba fizer a primeira
   apresentação completa, então deve explicar em linguagem natural:
   - o tipo de necessidade atendida;
   - o preço vigente por ambiente;
   - o limite de área aplicável;
   - que se trata de uma consultoria online;
   - o Manual do Espaço em PDF;
   - o Tour Virtual;
   - as três opções de solução;
   - as duas rodadas de alterações ou ajustes;
   - o suporte de três meses pelo WhatsApp;
   - as principais responsabilidades e exclusões relevantes para a dúvida.
2. Dada uma comparação entre serviços, quando uma opção for citada apenas como
   alternativa secundária, então a Urba pode resumir a explicação, mas deve
   deixar claro que pode detalhá-la sem sugerir que ela possui menos entregas
   comuns que os demais serviços.
3. Dada uma nova dúvida após a apresentação completa, quando a Urba responder,
   então deve focar na dúvida atual e não repetir todo o pacote.
4. Dada uma referência às três opções e aos ajustes, quando a Urba explicar essa
   entrega, então deve usar linguagem equivalente a:

> Com 3 opções de solução do espaço para você escolher, além de 2 rodadas de
> alterações ou ajustes do que você não gostar na solução apresentada.

5. A expressão “rodadas consolidadas” não deve aparecer para o cliente sem uma
   explicação simples.

### 2.3 Apresentação da Decor Reforma

1. Dada uma necessidade de reforma interna ou possível mudança técnica, quando a
   Urba apresentar a Decor Reforma, então deve comunicar todos estes fatos:
   - é uma consultoria online para reforma de ambiente interno;
   - custa R$ 450 por ambiente contratado;
   - atende até 20 m² por ambiente contratado;
   - demandas e mudanças técnicas dependem da avaliação da arquiteta;
   - cada ambiente contratado recebe sua própria solução;
   - inclui Manual do Espaço em PDF, Tour Virtual, três opções de solução e duas
     rodadas de alterações ou ajustes;
   - inclui três meses de suporte pelo WhatsApp para dúvidas sobre o Manual e as
     cores, sem significar garantia, visita ou gestão de obra;
   - a Urbana não executa a obra;
   - a Urbana não compra materiais nem contrata profissionais.
2. A apresentação pode usar como referência adaptável:

> Se houver necessidade de reforma ou mudanças técnicas, existe a Decor Reforma,
> nossa consultoria para reforma de ambientes internos com até 20 m², sujeita à
> avaliação da arquiteta. É como um mini projeto para cada ambiente contratado:
> você recebe um Tour Virtual mostrando como o espaço poderá ficar e o Manual do
> Espaço, em PDF, explicando os detalhes da solução. Não executamos a obra, não
> compramos materiais e não contratamos profissionais. 😉

3. A expressão “mini projeto” não pode ser usada para prometer projeto executivo,
   responsabilidade técnica, viabilidade, aprovação ou execução.
4. Dado um pedido que envolva estrutura, elétrica, hidráulica, gás, ART/RRT,
   projeto legal ou aprovação de condomínio ou prefeitura, quando a Urba
   responder, então deve informar a necessidade de avaliação da arquiteta sem
   confirmar que o item está incluído ou é viável.
5. Dado um ambiente acima de 20 m² ou cuja área não possa ser confirmada, quando
   a Urba responder, então não deve prometer o atendimento ou o preço padrão e
   deve encaminhar a avaliação para a arquiteta.

### 2.4 Aceite dos termos com “Aceito”

1. Dados os termos já apresentados para o serviço e ambiente atuais no fluxo
   Hermes usado pela POC, quando o cliente responder somente “Aceito”, com
   qualquer combinação de maiúsculas, espaços ou pontuação simples, então o
   aceite deve ser registrado e a conversa deve avançar sem pedir a mesma
   confirmação novamente.
2. Continuam válidas respostas explícitas como “aceito os termos”, “concordo com
   os termos” e “estou de acordo com os termos”.
3. Dado que os termos ainda não foram apresentados, quando a pessoa disser
   “Aceito”, então a fala isolada não deve criar um aceite antecipado.
4. Dada uma resposta negativa ou ambígua, como “não aceito”, “talvez”, “vou ler”
   ou “ok”, quando ela for recebida, então o aceite não deve ser registrado.
5. Dada a resposta “Aceito” seguida imediatamente da forma de pagamento, quando
   as mensagens forem processadas em sequência, então o aceite deve ser aplicado
   antes da tentativa de preparar o pagamento.
6. O cliente nunca deve receber uma mensagem dizendo que seu “Aceito” foi
   entendido e, em seguida, ser obrigado a repetir uma frase mais longa.
7. Dada uma mensagem única e inequívoca, como “Aceito, quero pagar no cartão”,
   quando os termos já estiverem apresentados, então o aceite e a forma de
   pagamento devem ser considerados na ordem correta.
8. Cada aceite válido deve ser auditável com serviço e ambiente associados. Um
   identificador de contratação pode representá-los desde que permita recuperar
   ambos. A evidência também registra versão ou recurso dos termos, instante de
   apresentação, instante de aceite e texto exato da confirmação do cliente.
9. A flexibilização aplica-se ao fluxo canônico Hermes e aos canais que o usam.
   O fluxo legado de WhatsApp/Gemini permanece fora do escopo desta feature.

### 2.5 Orientação de quantidade no pagamento

1. Dados serviço, ambiente, termos aceitos e forma de pagamento confirmados,
   quando a Urba enviar um link ou uma simulação de pagamento, então deve
   orientar o cliente a selecionar a quantidade de serviços desejada,
   considerando uma unidade para cada ambiente, sem depender de um player real
   estar disponível nesta etapa.
2. Para um único ambiente, a orientação deve permitir entender que a quantidade
   correta é uma unidade.
3. Para mais de um ambiente, a Urba não deve sugerir que uma única contratação
   cobre todos eles.
4. Ambientes diferentes do mesmo serviço podem usar o mesmo link, com uma
   unidade para cada ambiente. Serviços diferentes exigem termos, aceites e
   links separados; a orientação de quantidade não cria pacote, desconto ou
   condição especial.
5. A mensagem deve continuar pedindo o envio do comprovante após o pagamento.
6. A mensagem não deve prometer que o player possui um seletor enquanto essa
   capacidade não tiver sido verificada; a verificação do player e a alternativa
   para o caso negativo ficam registradas como TODO pré-homologação.

Exemplo de orientação:

> No link, selecione a quantidade de serviços que deseja contratar — considere
> 1 serviço para cada ambiente. Depois do pagamento, envie o comprovante por
> aqui.

### 2.6 Mensagens enviadas em sequência

1. Dada uma pergunta de perfil ainda sem resposta publicada, quando o cliente
   enviar uma ou mais mensagens antes da conclusão do turno em andamento, então
   a próxima resposta publicada deve reconciliar todas as entradas aceitas até
   aquele momento antes de repetir qualquer pergunta.
2. Dado um campo de perfil respondido explicitamente, quando uma resposta tardia
   de um turno anterior for publicada, então ela não deve pedir novamente o
   mesmo campo como se estivesse ausente.
3. O envio rápido de “Aceito” e da forma de pagamento não pode inverter a ordem
   comercial nem gerar uma nova solicitação de aceite.
4. Na coleta guiada, cada mensagem da Urba pergunta exatamente um campo de
   perfil. Se o cliente informar espontaneamente vários campos na mesma mensagem
   ou em mensagens consecutivas, todos devem ser aproveitados sem nova pergunta
   sobre os campos já respondidos.

## 3. Critérios de aceite

### 3.1 Voz e linguagem

- **CA-001**: a Urba usa linguagem casual e amigável nos momentos leves, sem
  parecer excessivamente formal, técnica ou burocrática.
- **CA-002**: pelo menos uma transição positiva do fluxo completo usa uma
  expressão casual coerente, sem repetir a mesma expressão automaticamente.
- **CA-003**: o fluxo completo contém pelo menos um emoji contextual em momento
  leve; cada mensagem usa no máximo um, e termos, pagamento, comprovante, falha,
  recusa, frustração e handoff não usam emoji algum.
- **CA-004**: nenhum termo técnico evitável fica sem equivalente ou explicação
  simples na conversa avaliada.
- **CA-005**: exemplos de voz não se tornam respostas idênticas em todas as
  execuções nem substituem a leitura do contexto real.

### 3.2 Catálogo e apresentação

- **CA-006**: a primeira apresentação completa de um serviço contém Manual do
  Espaço em PDF, Tour Virtual, três opções, duas rodadas de alterações, suporte
  de três meses, preço e limite de área aplicável.
- **CA-007a**: a apresentação completa da Decor Reforma informa R$ 450 por
  ambiente e o limite padrão de até 20 m² por ambiente.
- **CA-007b**: a apresentação completa da Decor Reforma deixa claro que é uma
  consultoria online e que mudanças técnicas dependem da avaliação da arquiteta.
- **CA-007c**: a apresentação completa da Decor Reforma inclui Manual do Espaço
  em PDF, Tour Virtual, três opções e duas rodadas de alterações ou ajustes.
- **CA-007d**: a apresentação completa da Decor Reforma explica os três meses de
  suporte pelo WhatsApp para dúvidas sobre Manual e cores, sem tratá-lo como
  garantia, visita ou gestão de obra.
- **CA-007e**: a apresentação completa da Decor Reforma informa que a Urbana não
  executa a obra, não compra materiais e não contrata profissionais.
- **CA-007f**: ambientes acima de 20 m² são encaminhados para avaliação sem
  promessa de atendimento, exceção ou preço padrão.
- **CA-007g**: pedidos envolvendo estrutura, instalações, ART/RRT ou aprovações
  são encaminhados para avaliação sem confirmação de inclusão ou viabilidade.
- **CA-008**: a frase “3 opções de solução e até 2 rodadas consolidadas de
  ajustes” não é apresentada isoladamente ao cliente.
- **CA-009**: preço, área, responsabilidades, exclusões e suporte permanecem
  coerentes com o catálogo comercial vigente.
- **CA-010**: “Manual do Espaço”, “Tour Virtual” e “suporte” mantêm essas
  denominações na primeira explicação relevante.

### 3.3 Termos e pagamento

- **CA-011**: “Aceito”, como resposta isolada após a apresentação dos termos,
  registra o aceite em 100% dos testes aplicáveis e preserva os dados de
  auditoria definidos em 2.4.8.
- **CA-012**: respostas negativas, antecipadas ou ambíguas não registram aceite.
- **CA-013**: após um “Aceito” válido, nenhuma mensagem pede repetição do aceite.
- **CA-013a**: “Aceito, quero pagar no cartão” e o par consecutivo “Aceito” +
  “cartão” registram primeiro o aceite e depois a forma de pagamento, sem
  tentativa prematura nem retrabalho.
- **CA-014**: toda primeira mensagem que envia um link ou uma simulação de
  pagamento inclui a orientação textual de uma unidade por ambiente e o pedido
  de comprovante.
- **CA-014a**: o relatório da POC registra explicitamente que a capacidade do
  player de selecionar quantidade ainda não foi verificada e não apresenta essa
  capacidade como fato observado.
- **CA-015**: a orientação de quantidade não comunica pacote, desconto ou
  cobertura de múltiplos ambientes por uma única unidade; serviços diferentes
  mantêm termos, aceites e links separados.

### 3.4 Continuidade e segurança

- **CA-016**: mensagens aceitas antes da publicação da resposta do turno em
  andamento são reconciliadas e não produzem pergunta de perfil duplicada sobre
  um campo já respondido.
- **CA-017**: a identidade virtual, a descoberta progressiva e a coleta guiada
  de exatamente uma pergunta de perfil por mensagem da Urba não regridem.
- **CA-018**: termos não são aceitos antes de serem apresentados; pagamento não é
  preparado antes do aceite; comprovante não confirma pagamento automaticamente.
- **CA-019**: solicitações técnicas de Decor Reforma continuam sujeitas à
  avaliação da arquiteta.
- **CA-020**: nenhuma informação comercial é obtida de links ou preços legados
  do roteiro de atendimento.

## 4. Edge Cases

- O cliente responde “ACEITO!”, “ aceito ” ou “Aceito.” após receber os termos.
- O cliente responde “não aceito”, “não sei se aceito”, “aceito depois”, “ok” ou
  apenas envia uma reação.
- O cliente diz “aceito” antes da apresentação dos termos ou durante a escolha
  do serviço.
- O cliente envia “Aceito” e “cartão” em duas mensagens rápidas.
- O cliente envia “Aceito, quero pagar no cartão” em uma única mensagem; a
  resposta deve ser interpretada pelo contexto sem relaxar os casos negativos.
- O cliente possui dois ambientes do mesmo tipo e pergunta se um pagamento cobre
  ambos.
- O cliente possui serviços diferentes no mesmo ambiente.
- O cliente solicita Decor Reforma para um ambiente acima de 20 m².
- O cliente pergunta se a Decor Reforma inclui ART/RRT, alteração estrutural,
  projeto elétrico ou aprovação de condomínio.
- O cliente pergunta sobre quantidade, parcelamento ou valor total sem ter
  informado quantos ambientes deseja contratar.
- A Decor Reforma é citada apenas como alternativa durante uma comparação.
- O cliente usa “mini projeto” como sinônimo de projeto executivo ou pede
  confirmação de responsabilidade técnica.
- O cliente está frustrado ou relata uma falha durante uma etapa normalmente
  leve; a emoção do contexto prevalece sobre o uso de gírias e emojis.
- O link de pagamento não permite selecionar quantidade; a Urba não deve afirmar
  uma capacidade inexistente e o caso precisa de tratamento operacional antes de
  disponibilização real.
- O cliente envia um comprovante após o link; a Urba transfere a validação ao
  humano e não confirma o pagamento automaticamente.
- O humano aprova o comprovante; só então o fluxo pode liberar o briefing e o
  próximo passo previsto no contrato vigente.

## 5. Observabilidade e validação

### 5.1 Gate 1 — consistência do contrato

Antes da implementação, a revisão deve confirmar que:

- os comportamentos não contradizem o catálogo comercial vigente;
- a nova aceitação de “Aceito” ocorre somente no contexto de termos apresentados;
- a descrição da Decor Reforma preserva avaliação humana e exclusões;
- a instrução de quantidade é tratada como orientação de mensagem e não como
  evidência de que um player real já oferece seleção;
- a evolução de voz não remove proteções em falha, handoff ou pagamento.

### 5.2 Gate 2 — validações determinísticas

Devem existir verificações automatizadas para:

- aceitar as variações válidas de “Aceito”;
- rejeitar negação, ambiguidade e aceite antecipado;
- aplicar aceite antes do pagamento em mensagens consecutivas;
- preservar a associação auditável entre o aceite e serviço, ambiente, recurso
  ou versão dos termos, instantes e texto da confirmação;
- manter a ordem termos → aceite → pagamento → comprovante;
- incluir quantidade por ambiente na primeira orientação de pagamento da
  simulação, sem alegar que um player real já foi validado;
- preservar preço, área, entregas, suporte e exclusões da Decor Reforma;
- encaminhar corretamente casos acima de 20 m² e demandas técnicas como ART/RRT;
- impedir duplicação de pergunta de perfil em mensagens rápidas;
- garantir que termos, pagamento, comprovante, falha e handoff não recebam emoji
  nem tom celebratório;
- preservar o handoff exclusivo após o comprovante e a liberação do briefing
  somente depois da aprovação humana.

### 5.3 Gate 3 — corpus conversacional

O corpus mínimo deve conter:

| ID | Cenário | Resultado obrigatório |
|---|---|---|
| C01 | Saudação simples | Apresentação curta, sem catálogo automático |
| C02 | Origem pelo Instagram | Acolhimento natural e pergunta útil |
| C03 | Pedido genérico sobre serviços | Descoberta progressiva, sem relatório |
| C04 | Apresentação da Decor Reforma | Fatos completos, linguagem humana e limite técnico |
| C05 | Apresentação da Decor Interiores | Entregas comuns e suporte presentes |
| C06 | Confirmação positiva do serviço | Transição casual e contextual, com um emoji adequado |
| C07 | “Aceito” após os termos | Aceite único e avanço para forma de pagamento |
| C08 | “Não aceito” ou resposta ambígua | Sem aceite e sem pagamento |
| C09 | Envio do link/simulação para um ambiente | Mensagem explica quantidade 1 e comprovante; não valida player |
| C10 | Envio do link/simulação para vários ambientes | Uma unidade por ambiente, sem pacote implícito; não valida player |
| C11 | Respostas rápidas de perfil | Sem pergunta duplicada |
| C12 | Dúvida técnica ou frustração | Clareza, sem promessa ou entusiasmo inadequado |
| C13 | Comprovante recebido | Sem aprovação automática; handoff humano exclusivo |
| C14 | Comprovante aprovado pelo humano | Briefing e próximo passo liberados somente após a aprovação |
| C15 | Decor Reforma acima de 20 m² | Sem promessa de preço ou atendimento; avaliação da arquiteta |
| C16 | Decor Reforma com ART/RRT ou demanda técnica | Sem confirmação de inclusão ou viabilidade; avaliação da arquiteta |

Os cenários comparativos C01–C12 formam os 12 pares baseline/candidata. Os
cenários variáveis C02, C03, C04, C05, C06 e C12 devem ser executados três vezes
com a candidata em sessões novas. C13–C16 são regressões operacionais e precisam
passar integralmente, mas não entram na preferência qualitativa entre os 12
pares. Variação natural de palavras é permitida; fatos e invariantes devem
permanecer estáveis.

### 5.4 Gate 4 — replay da conversa Yohanna

O baseline reproduzível está preservado em
[evidence/yohanna-baseline.md](evidence/yohanna-baseline.md), com os 31 eventos,
horários em UTC, estado final e o ponto de concorrência observado. A candidata
deve ser executada no frontend em uma conversa nova e sem histórico anterior,
enviando exatamente as mesmas mensagens de entrada, na mesma ordem.

Em cada passo, o executor aguarda a resposta terminar e ficar visível antes de
enviar a entrada seguinte, exceto no passo de concorrência identificado no
baseline: a resposta “sim” sobre a primeira contratação é enviada enquanto a
resposta ao nome ainda está sendo concluída. Esse passo é controlado pela ordem
dos eventos, e não pela tentativa de repetir um intervalo arbitrário de relógio.

A sequência funcional contém:

1. saudação;
2. origem pelo Instagram;
3. pedido de informações;
4. necessidade de organizar a varanda usada por uma manicure;
5. comparação entre Interiores e Reforma;
6. pergunta de valor para sala de estar;
7. escolha de decorar e organizar;
8. confirmação da Decor Interiores;
9. respostas de perfil, incluindo mensagens rápidas;
10. apresentação dos termos;
11. resposta isolada “Aceito”;
12. escolha de cartão;
13. envio do link com orientação de quantidade.

Equivalência semântica significa preservar intenção, fatos obrigatórios, ordem
comercial e próximo passo; não exige repetir as palavras da resposta baseline.
As falas de entrada, o contexto do ambiente, o serviço escolhido, os preços e os
estados observáveis precisam ser os definidos na evidência.

O replay comercial passa somente se:

- nenhuma pergunta de perfil respondida for repetida;
- “Aceito” for suficiente e não houver retrabalho;
- o link for enviado uma única vez, no estágio correto;
- a orientação de quantidade estiver presente;
- a conversa mantiver naturalidade e fatos comerciais corretos.

Após esse replay, a mesma sessão recebe uma evidência sintética de comprovante e
uma decisão humana simulada, em duas etapas distintas:

1. ao receber o comprovante, a Urba não confirma o pagamento, transfere a
   validação ao humano e deixa de responder autonomamente durante o handoff;
2. somente após a aprovação humana, o fluxo libera o briefing e o próximo passo
   previsto no contrato vigente.

Essas duas etapas verificam C13 e C14 e são obrigatórias para aprovar o replay.

O relatório deve preservar a transcrição integral, os estados observáveis e as
avaliações, sem expor segredos ou credenciais.

### 5.5 Gate 5 — avaliação qualitativa

Os 12 pares C01–C12 serão apresentados à PO em ordem aleatória e sem indicar
qual transcrição é baseline ou candidata. Cada transcrição será avaliada de 1 a
5 em:

- acolhimento;
- naturalidade de WhatsApp;
- linguagem casual sem exagero;
- clareza;
- completude comercial proporcional ao momento;
- correção factual;
- adequação de emoji e energia;
- clareza do próximo passo.

Cada transcrição soma de 8 a 40 pontos. Para um cenário:

- a candidata é **igual ou melhor** quando sua soma é maior ou igual à baseline,
  nenhuma dimensão fica abaixo de 3 e não há regressão de invariante objetivo;
- a candidata é **claramente preferível** quando supera a baseline em pelo menos
  4 pontos, nenhuma dimensão fica abaixo de 3 e não há regressão de invariante;
- empates ou ganhos qualitativos nunca compensam falhas de termos, pagamento,
  catálogo, handoff ou segurança.

O conjunto é aprovado quando:

- as dimensões da candidata alcançam média mínima 4,0 considerando C01–C12;
- 100% dos invariantes de termos, pagamento e segurança passam;
- a candidata é igual ou melhor em pelo menos 10 dos 12 pares;
- a candidata é claramente preferível em pelo menos 8 dos 12 pares;
- C13–C16 passam integralmente;
- o replay Yohanna é aprovado integralmente.

### 5.6 Evidência de entrega

A entrega deve conter:

- lista dos comportamentos modificados;
- validações automatizadas executadas e resultados;
- relatório do corpus com transcrições;
- replay manual Yohanna pelo frontend, incluindo comprovante e decisão humana;
- limitações da POC, especialmente a inexistência de player/link real e o link de
  pagamento apenas simulado;
- riscos residuais e itens que dependem de homologação;
- referência ao ticket e ao Pull Request aplicável.

## 6. Fora de escopo

- Alterar preços, descontos, prazos ou limites de área.
- Transformar a Urba em arquiteta ou responsável técnica.
- Prometer execução de obra, compra de materiais ou contratação de profissionais.
- Validar ou confirmar pagamento sem ação humana.
- Alterar o conteúdo jurídico dos termos de uso.
- Implementar o seletor de quantidade no provedor de pagamento.
- Validar uma transação financeira real através dos links simulados da POC.
- Reescrever o primeiro greeting determinístico do fluxo legado de WhatsApp.
- Migrar ou substituir as integrações existentes do Hermes.
- Fazer deploy em homologação ou produção como parte da criação desta spec.

## 7. TODO pré-homologação

Não há dúvida bloqueante para planejamento. Quando o player de pagamento for
escolhido, a operação deve verificar se cada link aplicável permite selecionar
quantidade de produtos/serviços. O resultado e, se necessário, a alternativa
operacional devem ser registrados antes da homologação; nenhum link real é
assumido ou validado por esta feature.

## 8. Entidades conceituais

- **Momento conversacional**: saudação, descoberta, apresentação, confirmação,
  termos, pagamento, falha ou handoff; determina energia e linguagem permitidas.
- **Apresentação de serviço**: conjunto proporcional de escopo, preço, área,
  entregas, suporte, responsabilidades e exclusões.
- **Aceite dos termos**: confirmação textual associada a termos já apresentados
  para um serviço e ambiente específicos.
- **Quantidade da contratação**: número de unidades no pagamento, sendo uma
  unidade correspondente a cada ambiente contratado.
- **Evidência conversacional**: transcrição e estados observáveis usados para
  comparar o comportamento com os critérios desta feature.

## 9. Critérios mensuráveis de sucesso

- 100% das respostas “Aceito” válidas avançam sem repetição.
- 100% das respostas negativas ou antecipadas permanecem sem aceite.
- 100% dos aceites válidos preservam os dados mínimos de auditoria.
- 100% das mensagens de link/simulação produzidas nesta feature contêm a
  orientação de uma unidade por ambiente e o pedido de comprovante.
- O relatório de validação registra que não há player/link real disponível e que
  a capacidade de selecionar quantidade permanece TODO pré-homologação.
- 100% das apresentações completas de serviço citam as cinco entregas comuns.
- 100% das apresentações completas de Decor Reforma preservam preço, área,
  entregas, suporte, avaliação da arquiteta e exclusões.
- Cada replay completo contém pelo menos um emoji adequado em um momento leve e
  nenhuma mensagem contém mais de um; etapas sensíveis não contêm nenhum.
- Nenhum cenário de mensagens rápidas repete um campo de perfil já respondido.
- 100% dos cenários C13–C16 preservam os limites técnicos, a validação humana do
  comprovante e o handoff exclusivo.
- Nenhuma resposta factual diverge do catálogo vigente.
- A avaliação qualitativa da candidata atinge média mínima 4,0 em cada dimensão,
  é igual ou melhor em pelo menos 10 dos 12 pares e claramente preferível em pelo
  menos 8 deles.
- A PO aprova integralmente o replay Yohanna pelo frontend.

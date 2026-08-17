# Feature Specification: Atendimento comercial completo e seguro da Urba

- **Feature Branch**: `008-complete-urba-service-flow`
- **Created**: 2026-08-17
- **Status**: Draft — aguardando GO de Emanuel
- **Input**: Materializar no atendimento da Urba todo o catálogo operacional refinado, corrigir o avanço comercial, impedir exposição de falhas internas, confirmar transferências humanas e viabilizar uma validação manual ponta a ponta.

## Metadados

- `Título da feature`: Atendimento comercial completo e seguro da Urba
- `Ticket Jira`: a criar ou vincular após o GO; relacionado a PEE-23, PEE-102, PEE-103 e PEE-104
- `Responsável pela spec`: Tech Lead Orchestrator
- `Aprovador de negócio`: Emanuel
- `Contexto de branch`: `feature/* -> hml -> main`
- `Fonte principal de negócio`: entrevista de refinamento da PEE-102, cujo contrato necessário está consolidado nesta spec
- `Diagnóstico de origem`: teste manual de 2026-08-17, cujas falhas relevantes estão consolidadas nesta spec

## 1. Contexto

### 1.1 Problema

O runtime atual já sustenta uma conversa mais contínua por meio do Hermes, mas
o comportamento testado ainda não representa o atendimento refinado com
Emanuel. A investigação da conversa manual de 2026-08-17 confirmou quatro
defeitos:

1. o catálogo apresentado ao agente contém somente nome, descrição curta e
   preço, sem processo, entregas, responsabilidades ou limites detalhados;
2. um checkpoint comercial exige dados de ICP que não foram coletados e que
   não constam como pré-requisito da contratação refinada;
3. a rejeição desse checkpoint chega ao agente como falha operacional e é
   transformada em uma fala que menciona problema no sistema;
4. a transferência humana funciona, mas a confirmação gerada para o cliente é
   descartada antes de entrar no histórico visível.

A PEE-102 consolidou o contrato de negócio, porém declara que sua implementação
não foi iniciada. PEE-103 e PEE-104 entregaram capacidades técnicas de retomada
de contexto e persistência transacional, mas não materializaram o catálogo nem
reescreveram os turnos comerciais normais. Esta feature fecha essa lacuna
funcional.

### 1.2 Objetivo

Permitir que a Urba conduza, sem engasgos e sem expor processos internos, uma
conversa que:

- descubra a necessidade do cliente;
- diferencie e explique os quatro serviços com precisão;
- apresente progressivamente entregas, processo e responsabilidades;
- avance com segurança por termos, pagamento e briefing;
- transfira para a arquiteta com uma confirmação clara;
- preserve o contexto durante o atendimento humano;
- retome proativamente quando houver uma próxima ação segura;
- permaneça fiel às decisões específicas da arquiteta;
- possa ser validada localmente de ponta a ponta antes de homologação.

### 1.3 Escopo funcional

Incluído:

- catálogo operacional canônico dos quatro serviços;
- respostas comparativas e explicações de “como funciona”;
- regras de área, múltiplos ambientes e serviços independentes;
- sequência comercial até termos, pagamento, validação humana e briefing;
- conhecimento sobre reunião, produção, entrega e suporte;
- erros comerciais recuperáveis e falhas técnicas com linguagem segura;
- transferência URBA → HUMANO com aviso visível e notificação interna;
- retomada HUMANO → URBA com contexto completo e possível mensagem proativa;
- controles de teste necessários para simular as decisões humanas;
- roteiro manual obrigatório cobrindo o comportamento refinado.

### 1.4 Fontes e precedência

Quando houver conflito, a ordem de precedência é:

1. decisão explícita da arquiteta registrada para a contratação específica;
2. estado validado da contratação e do atendimento;
3. esta spec e a PEE-102 de catálogo operacional;
4. especificações comerciais anteriores;
5. linguagem ou inferência genérica do agente.

Preços, links históricos e descrições legadas não podem substituir a fonte
canônica definida por esta feature.

### 1.5 Decisões adotadas para aprovação no GO

1. `PRONOUN_PREFERENCE`, `FIRST_TIME_HIRING` e `OCCUPATION` são informações
   opcionais e não bloqueiam termos, pagamento ou briefing.
2. Para apresentar os termos, os únicos pré-requisitos comerciais são serviço
   confirmado, ambiente identificado e área validada quando o serviço possuir
   limite padrão.
3. O ambiente local pode usar links de teste claramente identificados e sem
   valor comercial. Homologação e produção exigem recursos vigentes e
   aprovados.
4. A confirmação de handoff é determinística, curta e configurável; seu
   significado não depende de texto livre produzido pelo agente.
5. Nenhuma rejeição ou falha pode revelar sistema, ferramenta, integração,
   banco de dados, código, HTTP, exceção, retry, idempotência ou outro detalhe
   técnico ao cliente.
6. Uma mensagem enviada pelo cliente enquanto a conversa está em modo humano
   não reativa automaticamente a Urba.
7. A retomada proativa só ocorre quando o próximo passo estiver comprovado
   pelo estado; na dúvida, a Urba aguarda ou devolve o caso ao humano.

### 1.6 Entidades de negócio

- **Serviço**: tipo, nome, preço, disponibilidade, limite de área, escopo,
  entregas, exclusões, processo, responsabilidades e recursos vigentes.
- **Contratação**: combinação independente de cliente, serviço e ambiente.
- **Ambiente**: espaço a ser atendido, descrição e área quando aplicável.
- **Estado comercial**: descoberta, serviço sugerido, serviço confirmado,
  termos apresentados, termos aceitos, pagamento aguardado, pagamento em
  validação, pagamento confirmado e briefing.
- **Estado operacional**: briefing, agendamento, produção, entrega, suporte e
  encerramento.
- **Aceite de termos**: versão/recurso, contratação, envio, resposta textual e
  datas de auditoria.
- **Pagamento**: método, instrução vigente, comprovante recebido e decisão
  humana de validação.
- **Handoff humano**: motivo, resumo, responsabilidade atual, época da
  responsabilidade e estado de retomada.
- **Decisão humana**: orientação específica da arquiteta que prevalece sobre o
  catálogo geral naquele caso.
- **Mensagem canônica**: mensagem recebida ou enviada que compõe o histórico
  visível e a reidratação de contexto.

## 2. Comportamentos esperados

### 2.1 Cenário prioritário A — descoberta e explicação do serviço

1. Dado um novo contato, quando o cliente iniciar a conversa, então a Urba deve
   se identificar brevemente como assistente virtual da Urbana do Brasil e
   perguntar o que ele deseja transformar.
2. Dado que o cliente descreva uma necessidade, quando ainda faltar contexto,
   então a Urba deve fazer uma pergunta curta por vez.
3. Dado um quarto infantil em que o cliente quer pintura temática e pretende
   manter os móveis, quando a necessidade estiver clara, então a Urba deve
   sugerir Decor Pintura como hipótese, resumir o motivo e pedir confirmação.
4. Dado que o cliente pergunte a diferença entre Decor Interiores e Decor
   Pintura, quando a Urba responder, então deve distinguir layout/mobiliário de
   pintura/desenhos/tintas, informar o limite apenas de Interiores e não afirmar
   que Pintura possui limite de área.
5. Dado que o cliente confirme Decor Pintura e pergunte “como funciona”, quando
   a Urba responder, então deve explicar em linguagem curta e progressiva a
   consultoria online, o Manual, o Tour Virtual, as três opções, as duas rodadas
   e os próximos passos comerciais.
6. Dado que o cliente peça mais detalhes, quando a conversa continuar, então a
   Urba deve poder explicar responsabilidades, materiais, execução, prazo e
   suporte sem inventar ou encaminhar desnecessariamente.

### 2.2 Cenário prioritário B — termos, pagamento e briefing

1. Dado serviço, ambiente e área aplicável confirmados, quando o cliente quiser
   contratar, então a Urba deve apresentar os termos sem exigir dados opcionais
   de perfil.
2. Dado que os termos tenham sido apresentados, quando o cliente responder de
   forma ambígua, então a Urba deve pedir um aceite textual claro e não liberar
   pagamento.
3. Dado aceite textual claro, quando a contratação avançar, então a Urba deve
   perguntar o método e apresentar somente a instrução de pagamento vigente da
   contratação.
4. Dado que o cliente envie comprovante, quando o recebimento for registrado,
   então a Urba deve informar que a validação será humana, confirmar a
   transferência e parar de responder automaticamente.
5. Dado que a pessoa responsável confirme o pagamento e devolva a conversa,
   quando o contexto for retomado, então a Urba deve enviar proativamente o
   briefing correto sem pedir que o cliente repita o que aconteceu.
6. Dado briefing, medidas, fotos ou vídeos pendentes, quando o cliente pedir
   ajuda, então a Urba deve orientar com o material oficial e escalar apenas a
   dificuldade que não conseguir resolver.

### 2.3 Cenário prioritário C — handoff solicitado pelo cliente

1. Dado qualquer estágio automatizado, quando o cliente pedir uma pessoa ou a
   arquiteta, então a transferência deve ocorrer imediatamente.
2. No mesmo turno da transferência, o cliente deve receber exatamente uma
   confirmação curta de que a conversa foi encaminhada.
3. A arquiteta deve receber uma notificação interna com motivo, necessidade,
   serviço, dúvida, etapa e estados comerciais conhecidos.
4. Depois da confirmação, nenhuma nova resposta automática deve ser publicada
   enquanto a responsabilidade permanecer humana.
5. O indicador visual de atendimento humano pode complementar, mas nunca
   substituir, a mensagem conversacional de confirmação.

### 2.4 Cenário prioritário D — retorno da arquiteta para a Urba

1. Dado atendimento humano em andamento, quando a arquiteta tomar uma decisão,
   então essa decisão deve entrar no histórico canônico.
2. Dado que a arquiteta marque a conversa como responsabilidade da Urba,
   quando houver uma transição real HUMANO → URBA, então o histórico completo,
   estado e decisões devem ser sincronizados antes de qualquer resposta.
3. Dado próximo passo comprovado, quando a retomada terminar, então a Urba deve
   reconduzir proativamente o cliente.
4. Dado que não exista próximo passo seguro, quando a retomada terminar, então
   a Urba deve aguardar uma nova mensagem.
5. Dado erro terminal ou contexto insuficiente durante a retomada, então a
   responsabilidade deve continuar ou retornar ao humano sem mensagem técnica
   para o cliente.
6. Marcar novamente a responsabilidade da Urba sem nova transição não pode
   repetir sincronização nem mensagem proativa.

### 2.5 Catálogo operacional obrigatório

| Serviço | Preço por contratação | Área padrão | Explicação obrigatória |
|---|---:|---|---|
| Decor Interiores | R$ 400 | Até 20 m² por ambiente | Ambiente interno com layout, mobiliário, cores, materiais e composição; sem intervenção estrutural. |
| Decor Pintura | R$ 250 | Sem limite de m² | Pintura, desenhos e especificação de tintas; não inclui layout, mobiliário nem ensino prático de pintura. |
| Decor Fachada | R$ 350 | Sem limite de m² | Fachada, muro ou parede externa; pode considerar revestimentos, portão, iluminação e paisagismo conforme decisão da arquiteta. |
| Decor Reforma | R$ 450 | Até 20 m² por ambiente | Solução para reforma interna; demandas técnicas específicas dependem de avaliação da arquiteta. |

Entregas comuns aos quatro serviços:

- Manual em PDF;
- Tour Virtual, normalmente vídeo renderizado por link não listado;
- três opções de solução;
- duas rodadas consolidadas de ajustes;
- suporte de três meses após a entrega para dúvidas sobre Manual e cores.

Regras que a Urba deve conhecer e aplicar:

- a Urbana presta consultoria online e não executa obra ou pintura;
- a Urbana especifica, mas não compra materiais nem contrata profissionais;
- o cliente compra materiais, contrata mão de obra e responde pela execução;
- estoque, preços, links de compra e disponibilidade de mobiliário não são
  garantidos;
- o Tour Virtual é uma representação renderizada e seu detalhamento fica a
  critério da arquiteta;
- uma terceira rodada de ajustes é exceção decidida pela arquiteta e não pode
  ser prometida;
- o suporte não inclui visita, gestão de obra ou garantia do resultado;
- múltiplos ambientes e serviços distintos são contratações independentes;
- a Urba não oferece descontos;
- pessoas e pequenos negócios de qualquer região do Brasil podem contratar.

### 2.6 Processo operacional que deve ser explicável

1. descoberta e confirmação do serviço/ambiente;
2. apresentação dos termos e aceite textual independente;
3. pagamento integral antecipado, ainda que parcelado;
4. envio do comprovante e validação humana;
5. envio do briefing após pagamento confirmado;
6. envio obrigatório de medidas, fotos e/ou vídeos;
7. validação dos dados pela arquiteta;
8. agendamento pelo link de disponibilidade da arquiteta;
9. reunião online pelo Google Meet;
10. início da produção na data acordada;
11. prazo padrão de sete dias úteis a partir do início de produção;
12. pausa do prazo enquanto o cliente estiver pendente de feedback ou
    aprovação;
13. aprovação final explícita pelo cliente;
14. entrega formal por e-mail do Manual e do Tour Virtual;
15. suporte de três meses pelo WhatsApp.

### 2.7 Contrato de falhas e respostas seguras

| Situação | Comportamento visível esperado |
|---|---|
| Dado comercial obrigatório ausente | Fazer uma pergunta curta para coletar o dado necessário. |
| Dado opcional de perfil ausente | Continuar o fluxo sem bloquear. |
| Área desconhecida em Interiores/Reforma | Ajudar a estimar/medir; escalar apenas se a dificuldade persistir. |
| Área acima de 20 m² em Interiores/Reforma | Informar o limite padrão e encaminhar para avaliação, sem prometer exceção. |
| Link vigente indisponível | Não usar link legado; confirmar encaminhamento humano para obtenção segura. |
| Recurso temporariamente indisponível | Não mencionar tecnologia; tentar a recuperação segura prevista ou encaminhar com aviso claro. |
| Repetição da mesma ação | Reutilizar o resultado anterior ou aguardar, sem duplicar mensagem, aceite, cobrança ou handoff. |
| Falha após resultado possivelmente concluído | Verificar o estado antes de repetir a ação. |
| Falha terminal de retomada | Manter o atendimento humano e gerar alerta somente interno. |

Frases proibidas incluem, sem se limitar a:

- “o sistema não concluiu”;
- “tive um erro interno”;
- “a ferramenta falhou”;
- “a API/banco/integração está indisponível”;
- códigos, exceções ou detalhes de retry.

## 3. Critérios de aceite

### 3.1 Catálogo e conhecimento

- **FR-001**: deve existir uma única fonte canônica consumida pelo atendimento
  para os fatos operacionais dos serviços.
- **FR-002**: os quatro serviços devem estar disponíveis e usar nome, preço e
  limite de área definidos nesta spec.
- **FR-003**: o catálogo deve representar escopo, entregas, exclusões,
  responsabilidades, processo, suporte e disponibilidade de cada serviço.
- **FR-004**: a explicação de Decor Pintura nunca deve aplicar limite de área.
- **FR-005**: a explicação de Decor Interiores/Reforma deve validar 20 m² por
  ambiente e encaminhar exceções à arquiteta.
- **FR-006**: perguntas comparativas devem usar diferenças factuais, sem
  declarar ausência de informação que já esteja no catálogo.
- **FR-007**: respostas sobre “como funciona” devem começar curtas e permitir
  aprofundamento progressivo.
- **FR-008**: links legados ou não aprovados não podem aparecer como recurso
  comercial vigente.
- **FR-009**: uma atualização do catálogo deve valer para novas conversas sem
  criar uma segunda fonte concorrente.

### 3.2 Conversa e avanço comercial

- **FR-010**: a abertura deve identificar a Urba e conter uma única pergunta
  simples sobre o espaço ou necessidade.
- **FR-011**: a sugestão de serviço deve ser apresentada como hipótese, com
  mini resumo e pedido de confirmação.
- **FR-012**: a confirmação comercial do serviço deve ser separada do aceite
  formal dos termos.
- **FR-013**: dados opcionais de ICP não podem bloquear nenhuma etapa.
- **FR-014**: serviço, ambiente e área aplicável são os únicos dados de
  qualificação exigidos antes dos termos.
- **FR-015**: mudança de serviço antes do pagamento deve invalidar os termos da
  contratação anterior e reiniciar termos/aceite para a nova contratação.
- **FR-016**: múltiplos ambientes ou serviços devem manter estados e aceites
  independentes.
- **FR-017**: nenhuma ação de pagamento pode ocorrer antes de aceite textual
  claro e auditável.
- **FR-018**: “ok”, reação, silêncio ou pagamento não contam como aceite.
- **FR-019**: comprovante recebido deve permanecer pendente até decisão humana.
- **FR-020**: briefing só pode ser liberado após pagamento confirmado por
  pessoa autorizada.

### 3.3 Falhas seguras

- **FR-021**: toda rejeição esperada deve indicar internamente o dado ou estado
  ausente e oferecer uma próxima ação segura.
- **FR-022**: uma rejeição esperada não pode ser classificada como pane técnica
  na conversa.
- **FR-023**: falhas técnicas devem ser sanitizadas antes de qualquer texto
  visível ao cliente.
- **FR-024**: nenhum texto visível pode conter os termos técnicos proibidos da
  seção 2.7 ou detalhes equivalentes.
- **FR-025**: o atendimento nunca pode ficar silenciosamente preso; deve
  perguntar, aguardar explicitamente ou encaminhar.
- **FR-026**: retries e replays não podem duplicar termos, aceite, instrução de
  pagamento, comprovante, notificação ou mensagem.
- **FR-027**: o comportamento seguro deve ser o mesmo em resposta normal,
  timeout e reconciliação posterior.

### 3.4 Handoff e responsabilidade humana

- **FR-028**: pedido explícito de humano deve ser detectado em qualquer etapa e
  processado antes de nova tentativa comercial.
- **FR-029**: a mensagem que dispara o handoff deve receber exatamente uma
  confirmação canônica visível.
- **FR-030**: a mudança para modo humano só pode impedir mensagens automáticas
  posteriores à confirmação, não a própria confirmação.
- **FR-031**: a notificação interna deve conter o resumo mínimo definido na
  PEE-102 sem aparecer para o cliente.
- **FR-032**: mensagens recebidas durante modo humano devem ser persistidas e
  disponibilizadas à arquiteta sem resposta da Urba.
- **FR-033**: o indicador visual de ownership deve ser consistente com o
  histórico, mas não deve ser tratado como fala da Urba.
- **FR-034**: uma transferência repetida não deve criar confirmações ou
  notificações duplicadas.

### 3.5 Retomada HUMANO → URBA

- **FR-035**: somente uma transição real de responsabilidade pode iniciar a
  retomada.
- **FR-036**: nenhuma resposta pode ser enviada antes da sincronização e da
  decisão de retomada terminarem com sucesso.
- **FR-037**: histórico completo, decisões humanas e estado operacional devem
  estar disponíveis ao atendimento retomado.
- **FR-038**: o cliente não pode ser responsabilizado por repetir decisões da
  arquiteta.
- **FR-039**: decisão específica da arquiteta deve prevalecer sobre regra geral
  compatível com a contratação.
- **FR-040**: próximo passo comprovado deve gerar no máximo uma mensagem
  proativa.
- **FR-041**: ausência de próximo passo deve resultar em espera, sem mensagem
  inventada.
- **FR-042**: falha terminal deve manter/devolver o ownership humano e gerar
  somente observabilidade interna.
- **FR-043**: repetição, reordenação ou timeout não podem repetir histórico nem
  ação proativa.

### 3.6 Processo posterior e suporte

- **FR-044**: briefing, medidas, fotos e vídeos devem ser tratados como
  pré-requisitos da reunião/produção.
- **FR-045**: envio do link de agendamento deve transferir a responsabilidade
  formal à arquiteta.
- **FR-046**: decisões sobre reunião, produção, opções, ajustes, prazo e entrega
  devem permanecer sob responsabilidade da arquiteta.
- **FR-047**: prazo deve ser explicado como sete dias úteis a partir do início
  acordado, com pausa por pendência do cliente.
- **FR-048**: aprovação final deve ser explícita; silêncio não vale como
  aprovação.
- **FR-049**: entrega deve ser descrita como envio por e-mail do Manual PDF e
  link do Tour Virtual.
- **FR-050**: suporte deve ser descrito como três meses de dúvidas sobre Manual
  e cores, sem visita ou gestão da execução.

### 3.7 Critérios mensuráveis de sucesso

- **SC-001**: 100% dos quatro serviços passam na matriz factual de nome, preço,
  área, escopo e entregas.
- **SC-002**: o roteiro prioritário do quarto infantil chega aos termos sem
  handoff inesperado, bloqueio de ICP ou afirmação incorreta sobre área.
- **SC-003**: 100% das perguntas “como funciona” usadas no roteiro recebem
  explicação suficiente sem alegar falta de informação existente.
- **SC-004**: 100% dos casos de rejeição controlada resultam em pergunta ou
  encaminhamento seguro; 0% expõem detalhes internos.
- **SC-005**: cada handoff solicitado gera exatamente uma mensagem visível e
  exatamente uma notificação interna.
- **SC-006**: nenhuma mensagem automática é enviada após a confirmação enquanto
  o ownership permanecer humano.
- **SC-007**: em retomada com próximo passo conhecido, o cliente recebe no
  máximo uma mensagem proativa e não precisa repetir contexto.
- **SC-008**: 100% dos cenários manuais obrigatórios da seção 5.4 passam em uma
  execução limpa antes de promover a feature.

## 4. Edge Cases

- Cliente fornece nome, mas não preferência de tratamento, ocupação ou histórico
  de contratação.
- Cliente quer pintura temática e reaproveitar móveis; a Urba deve diferenciar
  Pintura de Interiores sem prometer layout em Pintura.
- Cliente não sabe a metragem ou informa medida aproximada.
- Cliente pede Interiores/Reforma acima de 20 m².
- Cliente contrata dois ambientes ou dois serviços no mesmo ambiente.
- Cliente muda de serviço depois dos termos, mas antes do pagamento.
- Cliente responde “ok” aos termos.
- Cliente envia comprovante antes de aceitar termos.
- Cliente envia comprovante duplicado.
- Recurso de termos ou pagamento não está vigente.
- A mesma ação comercial é repetida após timeout.
- O agente tenta avançar sem dado comercial obrigatório.
- O cliente pede humano no mesmo turno em que o agente pretendia avançar.
- O handoff é solicitado duas vezes.
- O agente gera texto de confirmação depois que a ferramenta já mudou o modo
  para humano.
- Chega nova mensagem do cliente durante atendimento humano.
- A arquiteta devolve a conversa sem registrar decisão nem próximo passo.
- A arquiteta devolve a conversa com decisão específica diferente da regra
  geral.
- A retomada recebe histórico repetido, atrasado ou fora de ordem.
- A sincronização conclui, mas a decisão de retomada falha.
- A mensagem proativa é confirmada, mas a resposta ao acionador sofre timeout.
- O cliente pede orientação prática para pintar, instalar ou executar obra.
- O cliente pede desconto, reembolso, cancelamento ou decisão técnica.
- O cliente pergunta por estoque, preço atual ou link de todos os materiais.
- O cliente fica pendente de feedback durante o prazo de produção.
- O cliente não aprova explicitamente a entrega.

## 5. Observabilidade e validação

### 5.1 Validação automatizada obrigatória

- testes de contrato do catálogo para os quatro serviços;
- testes de comparação entre serviços e regras de área;
- testes do avanço comercial com ICP opcional;
- testes de termos, aceite ambíguo, mudança de serviço e contratações
  independentes;
- testes de falhas recuperáveis e sanitização de falhas técnicas;
- testes de idempotência para ações comerciais e handoff;
- testes de confirmação canônica do handoff;
- testes que garantam silêncio automático somente após a confirmação;
- testes de retomada com contexto, decisão humana, ação proativa e espera;
- testes de timeout/reconciliação com a mesma semântica do caminho normal;
- testes de projeção do frontend separando mensagem de confirmação e indicador
  de atendimento humano;
- regressão do contrato Hermes e do isolamento do ambiente local.

### 5.2 Observabilidade interna

Cada turno comercial relevante deve permitir correlacionar internamente:

- contratação, conversa e turno;
- estágio antes/depois;
- ação comercial solicitada;
- resultado seguro ou classe de falha;
- handoff e ownership;
- sincronização/decisão de retomada;
- criação ou supressão justificada de mensagem canônica.

Logs e métricas não devem registrar conteúdo integral de cliente, segredos,
links sensíveis ou detalhes desnecessários. A observabilidade deve distinguir
rejeição de negócio, falha técnica, duplicidade, timeout e bloqueio humano.

### 5.3 Pré-condições do ambiente manual

O ambiente de validação deve oferecer meios seguros de:

- iniciar uma conversa limpa como cliente;
- observar mensagens canônicas e ownership;
- atuar como arquiteta durante o modo humano;
- registrar pagamento confirmado ou recusado;
- registrar decisão humana e devolver a responsabilidade à Urba;
- simular briefing recebido/validado, agendamento e entrega;
- usar termos, pagamento e briefing de teste inequivocamente não produtivos;
- inspecionar o resultado sem expor detalhes internos ao cliente.

### 5.4 Roteiro manual obrigatório

#### Roteiro A — conversa principal do quarto infantil

1. Iniciar uma conversa nova e informar nome.
2. Descrever quarto infantil com dinossauros/dragões.
3. Informar que deseja pintura e reaproveitamento dos móveis.
4. Confirmar que a Urba sugere Decor Pintura sem aplicar limite de área.
5. Perguntar a diferença entre Decor Pintura e Decor Interiores.
6. Confirmar Decor Pintura.
7. Perguntar “como funciona esse serviço?”.
8. Pedir detalhes sobre entregas, execução, materiais, prazo e suporte.
9. Confirmar que a Urba responde com o catálogo refinado, sem encaminhar e sem
   mencionar falta de informação.
10. Pedir contratação e confirmar que os termos são apresentados sem coleta
    obrigatória de ocupação, pronome ou primeira contratação.
11. Responder “ok” e confirmar que o pagamento não é liberado.
12. Dar aceite textual claro e escolher método de pagamento.
13. Confirmar instrução de teste vinculada à contratação correta.
14. Enviar comprovante de teste.
15. Confirmar mensagem de validação humana e exatamente uma confirmação de
    handoff.
16. Confirmar que mensagens posteriores não recebem automação durante modo
    humano.
17. Como arquiteta, validar o pagamento e devolver a conversa à Urba.
18. Confirmar que a Urba retoma com contexto e envia proativamente o briefing
    correto, sem pedir repetição.

#### Roteiro B — dificuldade no briefing e retorno

1. Durante briefing, relatar dificuldade simples de medir o ambiente.
2. Confirmar orientação com o material oficial.
3. Persistindo a dificuldade, pedir a arquiteta e confirmar o aviso de handoff.
4. Como arquiteta, registrar orientação específica e devolver a conversa.
5. Confirmar que a Urba respeita a orientação e reconduz ao briefing sem exigir
   que o cliente conte novamente o que foi decidido.

#### Roteiro C — agendamento, entrega e suporte simulados

1. Registrar briefing, medidas e mídia como validados.
2. Confirmar envio do link de agendamento e transferência para humano.
3. Como arquiteta, registrar reunião, decisão e entrega simulada.
4. Devolver a conversa à Urba após a entrega.
5. Perguntar sobre Manual, Tour Virtual, ajustes e suporte.
6. Confirmar respostas corretas e encaminhamento apenas para decisões
   específicas da arquiteta.

#### Roteiro D — falhas e proteções

1. Repetir uma ação comercial e confirmar ausência de duplicidade.
2. Simular dado comercial obrigatório ausente e confirmar pergunta segura.
3. Simular recurso de teste indisponível e confirmar que nenhuma fala menciona
   sistema, ferramenta ou código técnico.
4. Solicitar humano duas vezes e confirmar uma única mensagem/notificação.
5. Repetir a devolução HUMANO → URBA e confirmar uma única retomada proativa.

### 5.5 Gate de aprovação manual

A feature somente pode seguir para homologação quando:

- todos os testes automatizados obrigatórios estiverem verdes;
- os quatro roteiros manuais tiverem evidência registrada;
- nenhuma fala contiver linguagem técnica proibida;
- o histórico canônico e o ownership forem consistentes;
- não houver link legado apresentado como vigente;
- Emanuel aprovar a qualidade e naturalidade da conversa.

## 6. Fora de escopo

- deploy automático em homologação ou produção;
- pagamento financeiro real durante o teste local;
- validação automática do comprovante na primeira versão;
- execução de obra, pintura, compra de materiais ou contratação de profissional;
- garantia de estoque, preço ou disponibilidade de produtos;
- decisão autônoma sobre desconto, reembolso, cancelamento ou exceção técnica;
- substituição da arquiteta em reunião, produção, ajustes ou aprovação;
- envio real de e-mail/WhatsApp no ambiente local, quando um adaptador de teste
  puder comprovar o mesmo contrato;
- simulação do decurso real de sete dias úteis;
- alta disponibilidade do ambiente local;
- uso de memória global do agente como fonte canônica de negócio.

## 7. Dúvidas em aberto

Não há pergunta de negócio bloqueante para planejar após o GO. O GO de Emanuel
aprovará também as decisões da seção 1.5.

Pendências operacionais que devem ser resolvidas no planejamento, sem alterar o
contrato desta spec:

- definir a origem e a governança dos recursos vigentes de termos, pagamento,
  briefing e agendamento para cada ambiente;
- definir como o ambiente local simulará as ações exclusivas da arquiteta;
- associar esta feature ao ticket Jira de implementação e às dependências
  formais de PEE-103/PEE-104;
- definir quais evidências dos roteiros serão anexadas ao Jira/PR.

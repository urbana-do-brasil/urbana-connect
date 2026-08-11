# Feature Specification: Resiliência de turnos Hermes no chat manual da POC

**Feature Branch**: `003-hermes-turn-resilience`
**Consolidated branch**: `feat/pee-101`
**Created**: 2026-08-07
**Status**: Verified
**Input**: User description: "Corrigir a resiliência do processamento assíncrono de turnos Hermes na POC de chat manual, impedindo tentativas concorrentes e apresentando estados de espera e falha confiáveis."

## Metadados

- `Título da feature`: Resiliência de turnos Hermes no chat manual da POC
- `Ticket Jira`: A definir; correção derivada da POC manual vinculada a PEE-101
- `Responsável pela spec`: Tech Lead Orchestrator
- `Contexto de branch`: `feature/* -> hml -> main`

## 1. Contexto

A POC já aceita uma mensagem no chat local, persiste a entrada e entrega a
resposta pela projeção canônica da conversa. O navegador não precisa manter a
requisição de envio aberta: ele recebe o aceite e acompanha a conversa em segundo
plano por consultas periódicas. Esse modelo representa adequadamente o caráter
assíncrono do canal WhatsApp e deve ser preservado.

Em validação com o Hermes real, foram observados dois problemas relacionados:

- a chamada a Hermes pode ultrapassar o prazo interno da Urbana enquanto o Hermes
  ainda está executando o turno junto ao provedor de IA;
- a recuperação atual pode iniciar outro turno para a mesma sessão antes que o
  anterior tenha uma conclusão conhecida. Isso cria turnos concorrentes, torna a
  ordem do transcript incerta e pode deixar a pessoa testadora sem resposta;
- a interface deixa de acompanhar a conversa após uma espera fixa e mostra
  "Houve um problema técnico" mesmo quando a fonte canônica ainda informa que o
  processamento está em curso ou poderá produzir uma resposta.

O resultado desejado não é esconder indisponibilidade do Hermes ou do provedor.
É tornar o estado verdadeiro, preservar uma única execução por conversa e deixar
claro quando a resposta está apenas demorando, quando foi recebida e quando houve
falha terminal passível de nova tentativa.

### Atores

- **Pessoa testadora**: conversa com a Urba pelo chat local e precisa saber se a
  mensagem foi aceita, ainda está sendo processada ou exige nova tentativa.
- **Urbana Connect**: recebe a entrada sintética, coordena seu ciclo de vida,
  persiste o resultado canônico e impede duplicidade de turnos.
- **Hermes e provedor de IA**: dependência externa cuja latência ou
  indisponibilidade não pode ser tratada como uma resposta conversacional da
  Urba.

### Premissas e recomendação de escopo

- O fluxo de aceite assíncrono e consulta da projeção canônica continua sendo o
  mecanismo de entrega ao chat. Esta correção não exige WebSocket, SSE ou
  streaming no navegador.
- A resposta somente é exibida quando estiver registrada como mensagem canônica
  da conversa; a interface não inventa uma fala da Urba para cobrir erro técnico.
- Uma mesma conversa deve ter no máximo um turno remoto ativo ou com resultado
  ainda incerto. Conversas de contatos diferentes podem continuar independentes.
- A correção é restrita à POC local. Credenciais, modelo, provedor de IA, webhook
  real e integrações de produção permanecem inalterados.
- O ambiente precisa continuar distinguindo defeito da aplicação de uma falha
  real da dependência externa. Um smoke direto Hermes → provedor que falhe não
  deve ser reportado como sucesso da POC.

## 2. Comportamentos esperados

### US1 — Acompanhar uma resposta lenta sem falso erro (P1)

Como pessoa testadora, quero que o chat continue acompanhando uma mensagem aceita
enquanto a Urba está respondendo, para não confundir uma resposta lenta com uma
falha da ferramenta.

1. Dado que uma mensagem foi aceita e o turno ainda está em processamento, quando
   a espera ultrapassar o tempo usual de resposta, então o chat deve indicar que a
   resposta está demorando, sem transformar esse fato sozinho em erro terminal.
2. Dado que o processamento ainda está ativo, quando a pessoa mantiver o chat
   aberto, alternar de conversa ou recarregar a página, então o acompanhamento
   deve continuar ou ser retomado a partir do estado canônico.
3. Dado que a resposta canônica seja concluída após uma espera prolongada, quando
   ela estiver disponível, então deve aparecer uma única vez na conversa correta.

**Teste independente**: uma dependência controlada responde depois de um prazo
maior que os antigos limites de espera; a pessoa vê o estado de demora e recebe a
resposta sem reenviar a mensagem.

### US2 — Preservar um único turno por conversa (P1)

Como pessoa testadora, quero que uma mensagem em processamento não gere várias
execuções no Hermes, para que o histórico permaneça coerente e a resposta não se
perca entre tentativas concorrentes.

1. Dado que exista um turno ativo ou com conclusão ainda incerta para uma
   conversa, quando ocorrer timeout, nova consulta, recarregamento ou pedido de
   repetição, então o sistema não deve iniciar outro turno remoto para a mesma
   sessão.
2. Dado que novas mensagens cheguem para uma conversa cujo turno anterior ainda
   está ativo, quando elas forem aceitas, então devem permanecer preservadas em
   ordem para processamento posterior, sem concorrência com o turno em curso.
3. Dados contatos distintos com turnos ativos, quando ambos aguardarem resposta,
   então o isolamento por conversa deve ser mantido sem bloquear desnecessariamente
   as demais conversas.

**Teste independente**: simular demora, perda da resposta de transporte e
reenvios da mesma ação; a observação do Hermes e da persistência registra no
máximo um turno remoto ativo para a conversa afetada.

### US3 — Recuperar somente quando for seguro (P1)

Como pessoa testadora, quero que a recuperação automática ou manual seja segura,
para não causar uma segunda resposta ou embaralhar a memória da conversa.

1. Dado que uma tentativa tenha falhado antes de iniciar o processamento remoto ou
   tenha terminado de forma inequivocamente recuperável, quando a recuperação for
   executada, então ela deve reutilizar a identidade lógica da entrada e produzir
   no máximo uma resposta canônica.
2. Dado que uma chamada tenha excedido um prazo local e ainda possa estar sendo
   executada externamente, quando seu resultado não puder ser confirmado, então o
   sistema deve mantê-la em estado de conciliação ou demora, e não reenviá-la
   silenciosamente.
3. Dado que a execução anterior seja comprovadamente encerrada sem resposta, quando
   uma nova tentativa for liberada, então a pessoa deve receber uma indicação clara
   de que a falha foi terminal e de que a nova tentativa é segura.

**Teste independente**: provocar um timeout ambíguo e uma falha recuperável
inequívoca; somente o segundo caso pode gerar nova chamada remota automática.

### US4 — Exibir estado técnico verdadeiro e recuperável (P1)

Como pessoa testadora, quero entender o estado da mensagem sem receber uma fala
artificial da Urba, para decidir se devo continuar aguardando ou tentar novamente.

1. Dado que a mensagem esteja na fila ou em processamento, quando o chat consultar
   a conversa, então deve exibir um estado de espera não conversacional.
2. Dado que a resposta esteja demorando além da janela usual, quando o backend
   ainda indicar trabalho ativo ou em conciliação, então o chat deve exibir um
   estado de demora e continuar acompanhando-o.
3. Dado que o backend determine uma falha terminal, quando o chat a receber, então
   deve informar a falha técnica de forma simples, manter a mensagem original e
   oferecer apenas a ação de recuperação que seja segura naquele estado.
4. Dado que não exista mensagem canônica de saída, quando houver falha, então o
   chat não deve apresentá-la como se tivesse sido dita pela Urba.

**Teste independente**: simular estados de fila, demora, conclusão e falha
terminal; cada um aparece com linguagem distinta e nenhuma falha é exibida como
mensagem da assistente.

### US5 — Retomar depois de interrupção local (P2)

Como pessoa testadora, quero poder fechar, recarregar ou trocar de conversa sem
perder uma mensagem já aceita, para testar cenários reais sem reenvios inseguros.

1. Dado que o navegador seja fechado ou perca a conexão após o aceite, quando a
   pessoa retornar ao contato, então deve recuperar a mensagem e seu estado atual
   pela fonte canônica.
2. Dado que uma resposta tenha sido concluída durante a ausência da pessoa, quando
   ela retornar, então deve vê-la uma única vez no histórico correto.
3. Dado que uma consulta da interface falhe temporariamente, quando ela voltar a
   funcionar, então deve retomar a leitura sem criar uma nova entrada nem um novo
   turno remoto.

**Teste independente**: enviar uma mensagem, interromper a interface antes da
resposta e recarregá-la; o resultado final coincide com a persistência canônica e
não contém duplicidades.

### US6 — Diagnosticar a causa sem expor conteúdo sensível (P2)

Como pessoa responsável pela POC, quero identificar onde um turno demorou ou
falhou, para separar instabilidade do Hermes/provedor de um defeito da Urbana ou
do chat.

1. Dado um turno aceito, quando ele mudar de estado, então os registros técnicos
   devem permitir correlacionar entrada, conversa, sessão, tentativa, duração e
   motivo terminal sem expor credenciais ou conteúdo conversacional desnecessário.
2. Dado que o smoke direto do Hermes com o provedor falhe, quando a validação for
   executada, então o resultado deve classificar explicitamente a dependência
   externa como indisponível, sem atribuir a falha ao frontend.

**Teste independente**: uma falha induzida permite identificar, a partir de uma
correlação técnica, se o problema ocorreu antes do Hermes, no Hermes ou no
provedor externo.

## 3. Critérios de aceite

### Requisitos funcionais

- **FR-001**: Cada mensagem aceita MUST possuir um registro durável de ciclo de
  vida associado à sua conversa e à sua identidade lógica antes de qualquer
  processamento remoto.
- **FR-002**: O ciclo de vida MUST distinguir, no mínimo, os estados de aceito/em
  fila, processando, resposta demorada ou em conciliação, concluído e falha
  terminal. Um prazo somente da interface MUST NOT converter um estado ativo em
  falha terminal.
- **FR-003**: O fluxo existente de aceite assíncrono e consulta ao histórico
  canônico MUST permanecer compatível com o chat manual e com os clientes de
  teste já existentes.
- **FR-004**: Enquanto uma conversa possuir execução ativa ou de conclusão
  incerta, o sistema MUST NOT iniciar uma segunda execução remota para a mesma
  sessão ou lote lógico de mensagens.
- **FR-005**: Um timeout do transporte local MUST NOT, por si só, liberar uma nova
  tentativa concorrente nem descartar a proteção de exclusão do turno em curso.
- **FR-006**: Mensagens posteriores aceitas durante um turno ativo MUST ser
  preservadas em ordem e processadas somente quando a execução precedente tiver
  uma resolução segura.
- **FR-007**: Retentativas automáticas MUST ocorrer apenas para falhas cuja não
  execução remota ou encerramento remoto tenham sido confirmados. Casos ambíguos
  MUST exigir conciliação antes de nova execução.
- **FR-008**: Uma tentativa recuperada MUST manter a identidade lógica da entrada
  e MUST NOT gerar mensagens canônicas de saída duplicadas.
- **FR-009**: A interface MUST continuar consultando, ou retomar a consulta, de
  uma mensagem não terminal enquanto o backend a classificar como fila,
  processamento, demora ou conciliação. Pode reduzir a frequência de consulta,
  mas MUST NOT abandonar a conversa apenas por tempo decorrido.
- **FR-010**: A interface MUST distinguir visualmente espera normal, demora e
  falha terminal. O texto técnico deve ser neutro, compreensível e não atribuído à
  Urba como resposta conversacional.
- **FR-011**: Uma resposta só MUST ser exibida após ser recuperada da projeção
  canônica e MUST aparecer exatamente uma vez no contato correto, inclusive após
  recarga ou recuperação de rede.
- **FR-012**: A pessoa testadora MUST poder tentar novamente somente quando o
  backend informar que a ação é segura; ações repetidas durante processamento ou
  conciliação MUST apenas mostrar o estado existente, sem reenvio remoto.
- **FR-013**: Os registros e métricas MUST correlacionar a evolução do turno,
  duração, quantidade de tentativas e motivo de término, sem registrar segredos
  ou expor credenciais ao navegador.
- **FR-014**: A correção MUST manter o isolamento entre contatos e MUST permitir
  que conversas diferentes sejam processadas de forma independente.
- **FR-015**: A correção MUST permanecer exclusiva da POC local e MUST NOT alterar
  o webhook real, a entrega ao WhatsApp, credenciais, modelo ou provedor de IA.

### Resultados mensuráveis

- **SC-001**: Em teste de integração com uma resposta controlada entregue depois
  do antigo prazo interno de 30 segundos, a conversa conclui com uma única
  mensagem canônica de saída e uma única execução remota observada.
- **SC-002**: Em teste controlado com espera superior ao antigo limite visual de
  120 segundos, o chat continua ou retoma o acompanhamento, apresenta estado de
  demora e exibe a resposta quando ela for persistida, sem exigir reenvio manual.
- **SC-003**: Em timeout ambíguo, perda de conexão do cliente ou recarregamento do
  navegador, são observadas zero execuções remotas concorrentes para a conversa
  afetada e zero saídas canônicas duplicadas.
- **SC-004**: Em falha remota comprovadamente terminal, a mensagem original fica
  visível, o chat apresenta uma falha técnica não conversacional e uma nova
  tentativa só é liberada após o estado seguro ser registrado.
- **SC-005**: Em teste com pelo menos três contatos simultâneos, cada conversa
  mantém seu transcript e seu estado próprios, sem bloqueio global nem mistura de
  respostas.
- **SC-006**: Logs, métricas ou consulta de suporte permitem classificar cada caso
  de teste como concluído, demora/conciliação, falha da Urbana, falha do Hermes ou
  indisponibilidade do provedor, sem imprimir chaves ou segredos.
- **SC-007**: A suíte automatizada cobre espera prolongada, timeout ambíguo,
  retentativa segura, recarregamento e isolamento de conversas; todos esses testes
  passam antes da aceitação da correção.
- **SC-008**: Um smoke real Hermes → provedor é executado quando o ambiente tiver
  as chaves configuradas; sucesso comprova a rota externa, e falha é registrada
  como bloqueio externo explícito, não como aprovação simulada da integração.

## 4. Edge Cases

- O Hermes conclui a geração depois de a Urbana ter excedido seu prazo local de
  espera pelo transporte.
- A resposta foi produzida externamente, mas a conexão usada para recebê-la foi
  interrompida antes de a persistência canônica ser confirmada.
- Uma nova mensagem chega para a mesma conversa durante processamento, demora ou
  conciliação de uma mensagem anterior.
- A pessoa pressiona repetidamente a ação de tentar novamente ou recarrega o chat
  diversas vezes enquanto o turno está ativo.
- Uma conversa diferente deve seguir processando mesmo que outra esteja lenta ou
  travada.
- A proteção de exclusão expira ou o processo local reinicia durante uma chamada
  externa ainda potencialmente ativa.
- O Hermes retorna erro explícito, resposta vazia, resposta inválida, limite de
  taxa ou indisponibilidade do provedor.
- O provedor não responde dentro do prazo operacional definitivo, e não há como
  provar que ainda existe execução remota ativa.
- A consulta do histórico falha temporariamente depois que a entrada já foi
  aceita.
- A resposta canônica chega durante uma consulta de recuperação ou após a pessoa
  ter mudado para outro contato.
- A entrada é recebida mais de uma vez por retransmissão de rede.
- O ambiente local não possui chaves válidas ou o smoke direto Hermes → provedor
  está indisponível.

## 5. Observabilidade e validação

### Evidências técnicas exigidas

- Testes unitários descrevem as transições de estado, a regra de exclusão por
  conversa, a decisão de retentativa e a preservação de identidade lógica.
- Testes de integração usam uma dependência Hermes controlada para reproduzir
  resposta lenta, timeout ambíguo, erro terminal e conclusão posterior.
- Testes do frontend validam os estados de espera, demora, falha e retomada sem
  abandono após o antigo limite visual.
- Um teste ponta a ponta local cobre chat → Urbana → Hermes controlado →
  persistência → chat para uma resposta lenta e confirma uma única saída.
- A suíte existente do backend, do chat e os scripts Hermes relevantes continuam
  verdes; regressões devem ser corrigidas antes de declarar a feature concluída.

### Sinais operacionais esperados

- Cada mudança de estado de um turno possui correlação técnica, duração acumulada,
  número de tentativas e classificação do término.
- São mensuráveis pelo menos: turnos concluídos, demorados, em conciliação,
  falhados, retentativas seguras e tentativas concorrentes impedidas.
- O smoke direto Hermes → provedor é executado separadamente do E2E da aplicação,
  para que uma indisponibilidade externa seja diagnosticada sem mascarar a
  validação do fluxo local.

### Roteiro manual de aceite

1. Iniciar os serviços locais da POC e abrir o chat.
2. Enviar uma mensagem para um contato e provocar ou simular uma resposta lenta.
3. Confirmar o estado de demora, trocar de contato e recarregar a página.
4. Confirmar que a resposta posterior aparece uma vez no contato de origem.
5. Repetir o cenário com indisponibilidade terminal e confirmar que não há fala
   artificial da Urba nem opção insegura de reenvio.
6. Enviar mensagens para pelo menos três contatos e confirmar isolamento durante
   a espera.

## 6. Fora de escopo

- Introduzir WebSocket, SSE, streaming de tokens ou outro canal persistente para
  o navegador.
- Trocar OpenRouter, modelo, perfil Hermes, credenciais ou política de cobrança
  do provedor de IA.
- Tornar a disponibilidade do Hermes ou do provedor garantida; a correção deve
  apenas representar e recuperar falhas de modo seguro.
- Alterar webhook real, integração com WhatsApp, deploy, ambiente de homologação
  ou produção.
- Criar painel técnico, ferramenta operacional, fila distribuída nova ou redesenho
  amplo da arquitetura da POC.
- Apagar transcripts, sessões, fatos ou outros dados persistidos para recuperar
  um turno.

## 7. Dúvidas em aberto

Nenhuma decisão de produto bloqueia o planejamento. Os valores de prazo usual,
prazo operacional definitivo, frequência de consulta e atraso de retentativa
devem ser medidos e configurados no planejamento, sem fixar valores mágicos na
interface. A regra já definida por esta spec é: um prazo local nunca autoriza,
isoladamente, uma nova execução quando a conclusão remota ainda é incerta.

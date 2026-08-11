# Feature Specification: Chat local para testes manuais da Urba

**Feature Branch**: `002-poc-manual-chat`
**Consolidated branch**: `feat/pee-101`
**Created**: 2026-08-06
**Status**: Verified — chat local validado e consolidado em `feat/pee-101`
**Input**: User description: "Criar uma interface web local de chat para testar manualmente a conversa Hermes-first como ela seria percebida por um cliente no WhatsApp."

## Metadados

- `Título da feature`: Chat local para testes manuais da Urba
- `Ticket Jira`: Não informado
- `Responsável pela spec`: Tech Lead Orchestrator
- `Contexto de branch`: `feature/* -> hml -> main`

## 1. Contexto

A POC Hermes-first já permite simular conversas por chamadas de API, consultar a
projeção persistida e executar um corpus automatizado. Esse mecanismo valida o
núcleo conversacional, mas torna o teste exploratório manual mais trabalhoso:
quem testa precisa preparar payloads, controlar identificadores e interpretar
respostas fora de uma experiência de conversa.

Esta feature cria uma interface gráfica estritamente local para uma pessoa
testadora conversar com a Urba de forma semelhante à experiência do cliente no
WhatsApp. A interface é um instrumento de teste da POC, não um novo canal de
produção nem uma interface de operação interna.

O chat deve reutilizar o mesmo ingresso sintético, o mesmo agrupamento temporal,
o mesmo motor Hermes-first e o mesmo histórico canônico já usados pela POC. Ele
não pode alterar o webhook real nem afirmar que valida a entrega pela plataforma
do WhatsApp.

### Atores

- **Pessoa testadora**: cria contatos locais, envia mensagens e observa as
  respostas da Urba como um cliente as receberia.
- **Urba**: processa as mensagens pelo núcleo Hermes-first e devolve as respostas
  conversacionais aprovadas pelas regras de domínio.

### Premissas

- O ambiente local da POC, incluindo Urbana Connect, Hermes e MongoDB, já está
  configurado e saudável.
- A ferramenta é executada somente na máquina da pessoa testadora e nunca é
  promovida para produção.
- Cada contato visual recebe uma identidade técnica opaca e estável.
- O nome amigável informado ao criar um contato é metadado exclusivo da
  interface e não participa da mensagem, do prompt, dos fatos nem da memória do
  Hermes.
- O histórico conversacional persistido no backend é a fonte de verdade. O
  navegador mantém apenas metadados de interface necessários para reencontrar
  os contatos locais.
- A primeira versão aceita somente texto. Mídias e ações humanas serão avaliadas
  em uma evolução posterior.

## 2. Comportamentos esperados

### US1 — Conversar manualmente com a Urba (P1)

Como pessoa testadora, quero enviar mensagens em uma interface de chat e receber
as respostas da Urba, para avaliar manualmente a naturalidade e o comportamento
da POC sem preparar chamadas de API.

1. Dado que o ambiente local está saudável, quando a pessoa abrir o chat e
   enviar um texto válido, então a mensagem deve aparecer imediatamente como
   enviada pelo cliente.
2. Dado que uma mensagem foi aceita, quando a janela real de agrupamento terminar
   e a Urba concluir o turno, então a resposta deve aparecer uma única vez como
   mensagem recebida.
3. Dado que a pessoa envie vários fragmentos dentro da janela de agrupamento,
   quando o turno for processado, então os fragmentos devem seguir o mesmo
   comportamento de agrupamento da POC, sem processamento forçado pela interface.
4. Dado que a Urba ainda esteja processando, quando não houver resposta disponível,
   então o chat deve indicar espera sem criar conteúdo conversacional artificial.

**Teste independente**: uma pessoa inicia um contato, envia mensagens textuais
fragmentadas e recebe a resposta da Urba pelo fluxo Hermes-first sem usar terminal
ou inspecionar o banco de dados.

### US2 — Manter múltiplos contatos isolados (P1)

Como pessoa testadora, quero alternar entre contatos independentes, para simular
conversas simultâneas e verificar isolamento e continuidade.

1. Dada a criação de um contato com nome amigável, quando o contato for salvo,
   então ele deve receber uma identidade técnica única que não revele nem envie o
   nome amigável ao Hermes.
2. Dados dois ou mais contatos, quando mensagens forem enviadas de forma
   intercalada, então cada histórico, memória e resposta deve permanecer no
   contato correto.
3. Dada uma resposta recebida em uma conversa não selecionada, quando ela chegar,
   então essa conversa deve apresentar um indicador de conteúdo não lido.
4. Dado um contato já existente, quando a pessoa voltar a selecioná-lo, então seu
   histórico persistido deve ser recuperado e a conversa deve continuar com a
   mesma identidade.
5. Dado que dois contatos tenham o mesmo nome amigável, quando ambos conversarem,
   então eles ainda devem permanecer tecnicamente distintos.

**Teste independente**: três contatos com nomes iguais ou diferentes trocam
mensagens intercaladas, recebem as respectivas respostas e não apresentam
vazamento de conteúdo ou memória.

### US3 — Retomar testes após recarregar a interface (P1)

Como pessoa testadora, quero fechar ou recarregar a interface e reencontrar meus
contatos, para continuar cenários recorrentes sem recriar manualmente o estado.

1. Dado um conjunto de contatos locais, quando a página for recarregada, então a
   lista de contatos e a seleção ativa devem ser restauradas.
2. Dado um contato restaurado, quando seu histórico for exibido, então as
   mensagens devem vir da fonte canônica do backend, ordenadas e sem duplicação.
3. Dado que um contato seja ocultado da lista local, quando essa ação for
   confirmada, então nenhum transcript, fato ou estado persistido no backend deve
   ser apagado.
4. Dado que uma nova identidade seja criada, quando a primeira mensagem for
   enviada, então ela não deve herdar memória de nenhum contato anterior.

**Teste independente**: após conversar com dois contatos, a pessoa recarrega o
navegador, recupera ambos os históricos e cria um terceiro contato sem memória
prévia.

### US4 — Continuar usando o chat durante processamentos em segundo plano (P2)

Como pessoa testadora, quero mudar de conversa enquanto uma resposta está sendo
processada, para testar contatos simultaneamente sem bloquear a interface.

1. Dado um contato aguardando resposta, quando a pessoa selecionar outro contato,
   então o primeiro deve continuar processando em segundo plano.
2. Dadas várias conversas em processamento, quando as respostas chegarem fora de
   ordem, então cada resposta deve aparecer somente no contato correspondente.
3. Dada uma conversa já aberta, quando sua resposta chegar, então ela deve ser
   exibida sem marcar incorretamente a própria conversa como não lida.

**Teste independente**: a pessoa envia mensagens para três contatos antes das
respostas e consegue continuar navegando enquanto cada resultado chega ao local
correto.

### US5 — Compreender e recuperar falhas transitórias (P2)

Como pessoa testadora, quero receber uma indicação simples quando a Urba não
conseguir responder, para distinguir falha técnica de silêncio conversacional e
tentar novamente com segurança.

1. Dado que uma mensagem esteja aguardando resposta, quando uma dependência ficar
   indisponível ou o tempo máximo de espera for excedido, então o chat deve exibir
   um aviso não conversacional e manter a mensagem original visível.
2. Dada uma falha transitória, quando a recuperação automática for possível,
   então o chat deve recuperar o resultado sem duplicar a entrada nem a resposta.
3. Dado que a recuperação automática não resolva, quando a pessoa escolher tentar
   novamente, então a operação deve preservar a identidade da mensagem e evitar
   um segundo efeito conversacional.
4. Dado um erro técnico sem mensagem canônica do backend, quando ele for
   apresentado, então o chat não deve atribuir à Urba uma fala inventada; uma
   resposta de contingência persistida pelo backend continua sendo exibida como
   resposta legítima da conversa.

**Teste independente**: uma indisponibilidade temporária é provocada durante um
turno; a interface sinaliza a falha e recupera ou repete a consulta sem duplicar
mensagens.

## 3. Critérios de aceite

### Requisitos funcionais

- **FR-001**: A ferramenta MUST oferecer uma experiência de chat focada
  exclusivamente na perspectiva do cliente, sem painel técnico, métricas, fatos,
  chamadas de ferramentas ou identificadores internos visíveis.
- **FR-002**: A ferramenta MUST permitir criar, selecionar, ocultar e retomar
  múltiplos contatos locais independentes.
- **FR-003**: Cada contato MUST possuir uma identidade técnica opaca, única e
  estável durante sua existência local.
- **FR-004**: O nome amigável do contato MUST permanecer somente na interface e
  MUST NOT ser incluído em mensagens, contexto, fatos, memória ou qualquer dado
  enviado ao Hermes.
- **FR-005**: A ferramenta MUST aceitar mensagens textuais não vazias dentro dos
  limites admitidos pela POC e MUST apresentar validação compreensível para
  entradas inválidas.
- **FR-006**: A ferramenta MUST usar o fluxo sintético Hermes-first existente e
  MUST NOT encaminhar mensagens ao webhook real ou à plataforma do WhatsApp.
- **FR-007**: A ferramenta MUST respeitar a janela real de agrupamento de
  fragmentos e MUST NOT forçar o processamento imediatamente após cada envio.
- **FR-008**: Mensagens enviadas MUST aparecer imediatamente no contato correto,
  com estado visual de processamento enquanto a resposta não estiver disponível.
- **FR-009**: Cada resposta canônica MUST aparecer exatamente uma vez, no contato
  correto e na ordem observada no histórico persistido.
- **FR-010**: A pessoa MUST poder mudar de contato enquanto outros turnos continuam
  em processamento.
- **FR-011**: Respostas recebidas em conversas não selecionadas MUST produzir um
  indicador de conteúdo não lido até que a conversa seja aberta.
- **FR-012**: Ao recarregar a página, a ferramenta MUST restaurar a lista local de
  contatos e recuperar os históricos da fonte canônica do backend.
- **FR-013**: O navegador MUST NOT ser a fonte de verdade do conteúdo das
  conversas nem criar uma cópia persistente completa dos transcripts.
- **FR-014**: Ocultar um contato da interface MUST NOT excluir mensagens, fatos,
  sessões ou auditoria persistidos no backend.
- **FR-015**: Retentativas e recuperações MUST preservar a idempotência da entrada
  e MUST NOT criar respostas ou efeitos duplicados.
- **FR-016**: Erros técnicos originados na interface ou no transporte MUST ser
  exibidos como estado da ferramenta, nunca como fala atribuída à Urba; respostas
  canônicas persistidas pelo backend MUST continuar sendo exibidas normalmente.
- **FR-017**: A interface MUST preservar quebras de linha e links presentes nas
  respostas textuais, sem interpretar conteúdo como marcação executável.
- **FR-018**: A ferramenta MUST ser iniciada junto com o ambiente local da POC por
  um único fluxo documentado de inicialização.
- **FR-019**: A ferramenta MUST estar disponível apenas pela interface local da
  máquina e MUST NOT ser incluída em configurações de produção.
- **FR-020**: Nenhuma credencial interna da POC MUST ser exposta em código entregue
  ao navegador, armazenamento do navegador, mensagens ou logs da interface.
- **FR-021**: A interface MUST usar identidade visual da Urbana e indicar que se
  trata de um simulador local, sem se apresentar como produto oficial do WhatsApp.
- **FR-022**: A interface MUST permitir envio por teclado, quebra de linha
  intencional e navegação básica sem exigir uso exclusivo do mouse.

### Resultados mensuráveis

- **SC-001**: Uma pessoa com o ambiente previamente configurado consegue iniciar
  todos os serviços e abrir o chat usando um único fluxo documentado, sem executar
  um servidor frontend manualmente.
- **SC-002**: Em um teste com três contatos e pelo menos cinco mensagens
  intercaladas por contato, 100% das mensagens e respostas aparecem somente no
  contato correto.
- **SC-003**: Em um teste com três fragmentos enviados dentro da janela de
  agrupamento, o backend registra um único turno conversacional e a interface não
  dispara processamento antecipado.
- **SC-004**: Após recarregar o navegador, 100% dos contatos locais permanecem
  acessíveis e seus históricos coincidem com a fonte canônica, sem duplicações.
- **SC-005**: Em falha induzida antes e depois da aceitação de uma mensagem, a
  recuperação não produz nenhuma entrada ou resposta duplicada.
- **SC-006**: Inspeção do conteúdo entregue ao navegador e de seu armazenamento
  encontra zero credenciais internas e zero cópias persistentes completas dos
  transcripts.
- **SC-007**: Uma resposta já disponível no backend aparece na conversa aberta em
  até 2 segundos durante operação local saudável.
- **SC-008**: Os fluxos principais de criar contato, enviar texto, alternar
  conversa, receber resposta e retomar histórico podem ser concluídos apenas pela
  interface gráfica.

## 4. Edge Cases

- Dois contatos são criados com o mesmo nome amigável.
- O nome amigável está vazio, contém somente espaços ou excede o limite visual.
- A pessoa envia texto vazio, somente espaços ou acima do limite aceito pela POC.
- A pessoa pressiona o comando de envio repetidamente antes da primeira resposta.
- A pessoa envia novos fragmentos no limite da janela de agrupamento.
- A conversa é trocada ou a página é recarregada durante o processamento.
- Respostas de contatos diferentes chegam fora da ordem de envio global.
- O aceite da mensagem é confirmado, mas a resposta demora além do esperado.
- A conexão é interrompida antes de ser possível saber se a entrada foi aceita.
- A mesma entrada ou resposta é observada mais de uma vez durante acompanhamento
  ou recuperação de falha.
- Urbana Connect, Hermes ou MongoDB fica indisponível durante um turno.
- O backend devolve resposta inválida, vazia ou estado terminal sem mensagem.
- A conversa entra em handoff humano e deixa corretamente de receber respostas
  automáticas.
- O navegador bloqueia ou limpa o armazenamento local usado para a lista de
  contatos.
- Um contato ocultado precisa ser recuperado por sua identidade técnica conhecida.
- O histórico contém volume suficiente para exigir carregamento incremental e
  preservação da posição de leitura.
- Uma resposta contém quebras de linha, URL, caracteres especiais ou texto que
  se parece com HTML.

## 5. Observabilidade e validação

- Testes automatizados de estado da interface cobrem criação e restauração de
  contatos, seleção, indicadores de não lido, processamento concorrente e falhas.
- Testes de contrato cobrem envio textual, acompanhamento assíncrono, recuperação
  do histórico, autenticação interna e idempotência.
- Testes de navegador cobrem os cinco cenários independentes das histórias de
  usuário em um ambiente local controlado.
- A suíte existente do núcleo Hermes-first continua verde e confirma que a nova
  interface não altera o webhook real nem as regras conversacionais.
- Um smoke test manual cria pelo menos três contatos, envia fragmentos
  intercalados, recarrega a página e verifica continuidade e isolamento.
- Logs técnicos podem registrar estado de requisição, identificador opaco e
  correlação, mas não devem registrar credenciais nem conteúdo conversacional
  desnecessário.
- A validação de segurança inspeciona os artefatos entregues ao navegador, o
  armazenamento local e as requisições para confirmar ausência de credenciais.

## 6. Fora de escopo

- Implantação em homologação ou produção.
- Conexão com o webhook real ou envio pela plataforma do WhatsApp.
- Validação de entrega, leitura, templates, número de telefone ou credenciais da
  Meta.
- Upload ou envio de áudio, imagem, documento e comprovante de pagamento.
- Aprovação humana de pagamento ou qualquer outra ação administrativa.
- Botões, listas e demais mensagens interativas do WhatsApp.
- Painel técnico com fatos, métricas, ações, ferramentas, traces ou documentos do
  banco de dados.
- Exclusão de transcripts, fatos, sessões ou auditoria persistida.
- Login, gestão de usuários, permissões ou compartilhamento entre máquinas.
- Notificações do sistema operacional.
- Aplicativo móvel nativo ou reprodução visual exata do WhatsApp.
- Substituição do corpus automatizado ou dos testes de integração existentes.

## 7. Dúvidas em aberto

Não existem dúvidas bloqueantes para o planejamento. A evolução para anexos será
especificada separadamente após a validação manual bem-sucedida deste MVP.

# Research: Atendimento comercial completo e seguro da Urba

## Decision 1 — manter a autoridade no backend

**Decision:** o catálogo, a contratação, os checkpoints de pagamento/briefing,
ownership e transcript continuam sendo decididos pelo backend Java. O Hermes
recebe resultados tipados e o SOUL orienta o checkpoint conversacional de ICP;
o plugin não contém regras ou preços e o backend não cria um hard gate de ICP.

**Rationale:** a investigação encontrou uma divergência entre o fixture usado
por `CommercialPolicyService`, o seed Mongo em `servicecatalog` e o contrato
exposto ao plugin. Uma fonte de decisão no backend impede que o modelo invente
limites, links ou estados e permite auditoria/idempotência.

**Alternatives considered:** deixar o catálogo no prompt/plugin ou fazer o
Hermes consultar diretamente Mongo. Rejeitadas porque duplicam fatos,
expõem armazenamento e tornam impossível garantir as proteções comerciais
hard do backend.

## Decision 2 — resultado de negócio distinto de falha técnica

**Decision:** rejeições esperadas de requisitos comerciais (dado obrigatório
ausente, serviço fora do limite, aceite inválido, recurso não vigente) devem
retornar um envelope estruturado
com código estável, próxima ação segura e campos necessários. Exceções técnicas
devem ser logadas com correlação e convertidas em mensagem neutra antes de
chegar à sessão Hermes ou ao cliente.

**Rationale:** o log de 2026-08-17 mostrou `PREPARE_TERMS` falhando por um
checkpoint de ICP tratado como rejeição de ferramenta, sendo persistido como
FAILED, devolvido como HTTP 500/409 e transformado pelo modelo em “o sistema
não concluiu”. O checkpoint agora é conversacional; se o Hermes o ignorar,
isso gera observabilidade interna, não uma falha visível nem rollback do fluxo.

O checkpoint de ICP é uma orientação conversacional e não pertence a esse
envelope de rejeição; sua ausência gera somente observabilidade
`ICP_SKIPPED_BEFORE_TERMS`.

**Alternatives considered:** deixar o modelo interpretar a exceção ou retornar
somente texto livre. Rejeitadas por vazamento técnico e por não permitirem
testes determinísticos.

## Decision 3 — handoff confirma antes de bloquear

**Decision:** a transição bem-sucedida grava a confirmação canônica como
mensagem visível exatamente uma vez, e só então bloqueia qualquer resposta
automática subsequente. A notificação interna e a mensagem externa são eventos
separados, cada um idempotente.

**Rationale:** o fluxo atual muda `ReceptionMode` para `HUMAN` antes de
`appendOutbound`, descartando a confirmação que o Hermes já havia gerado.
O frontend só mostra o indicador, por isso o cliente não é avisado pela
conversa.

**Alternatives considered:** depender apenas do banner do frontend ou deixar o
modelo redigir o aviso. Rejeitadas porque não são auditáveis no transcript e
podem variar/ser descartadas.

## Decision 4 — retomada segura é transição, não nova mensagem do cliente

**Decision:** a retomada usa o evento/contrato interno já previsto por PEE-102,
sincroniza o transcript humano até um limite, decide entre ação proativa e
espera, e só libera o turno normal após sucesso. Repetições são no-op/replay e
falhas terminais fecham para o lado humano.

**Rationale:** a sessão Hermes é projeção; mensagens da arquiteta ocorridas no
modo humano não entram automaticamente nela. Forjar uma mensagem `CONTACT`
perderia a autoridade e poderia gerar cobrança ou briefing sem decisão.

**Alternatives considered:** concatenar o histórico humano na próxima fala do
cliente ou pedir que a arquiteta digite um template. Rejeitadas pela perda de
semântica, duplicidade e responsabilidade indevida ao cliente/arquiteta.

## Decision 5 — fixtures locais são explicitamente não comerciais

**Decision:** o local usa links/ações de teste identificados como fixture e
controlos determinísticos para validação humana. Recursos produtivos não entram
no repositório nem no compose local.

**Rationale:** a spec autoriza teste ponta a ponta sem usar links legados ou
financeiros reais; isso preserva a possibilidade de validar o comportamento
sem transformar a POC em canal de contratação.

**Alternatives considered:** reutilizar links atuais do site ou de produção.
Rejeitadas por risco operacional e por não serem recursos aprovados para o
ambiente local.

## Decision 6 — ICP é checkpoint conversacional, não bloqueio de domínio

**Decision:** depois de serviço confirmado e intenção explícita de contratar, o
SOUL deve coletar somente os campos de ICP ausentes antes de orientar os termos.
Há uma segunda tentativa apenas para campos faltantes; recusa ou ausência após
essa tentativa vira `NÃO INFORMADO` e o fluxo avança. O backend não rejeita
`prepare_terms` por ICP incompleto, mas registra
`ICP_SKIPPED_BEFORE_TERMS` quando detectar o desvio. Pergunta, tentativa, pausa
e retomada são interpretadas pelo SOUL sobre a thread e os fatos correntes; não
há `ICPCheckpointState`, contador autoritativo ou controlador de diálogo
persistido no backend.

**Rationale:** Emanuel confirmou que a coleta é importante para Growth, mas que
forçar o cliente ou introduzir um bloqueio de implementação já causou regressão
de conversa no passado. O contrato preserva a experiência conversacional e
torna a falha detectável por testes/observabilidade.

**Alternatives considered:** tornar os três campos pré-requisito de backend,
persistir uma máquina de estado apenas para controlar a coleta ou deixar a
coleta totalmente opcional. As duas primeiras alternativas podem
travar/confundir o Hermes; a terceira permite que os termos apareçam antes do
enriquecimento sem qualquer detecção.

## Decision 7 — fatos explícitos e contexto integral

**Decision:** qualquer declaração explícita do cliente pode preencher o ICP em
qualquer momento, com atualização silenciosa pelo valor mais recente. O Hermes
recebe a thread atual integral, incluindo mensagens da arquiteta, além do
contexto associado do cliente; threads antigas inteiras não são anexadas.

**Rationale:** suprimir histórico ou enviar uma projeção estreita já produziu
perda de contexto no Hermes. A íntegra permite que o núcleo resolva a conversa;
as regras de precedência ficam no SOUL e a origem do fato continua limitada à
declaração explícita do cliente.

**Alternatives considered:** enviar apenas um perfil “limpo” com o valor atual
ou manter um snapshot imutável da primeira interação. Rejeitadas porque podam
contexto e adicionam complexidade que não entrega ganho proporcional para este
objetivo de enriquecimento.

## Decision 8 — observabilidade sem conteúdo bruto

**Decision:** o perfil/fato pode armazenar o texto necessário para a conversa e
seu histórico de versões; logs e métricas registram apenas campo, status,
origem, momento, conversa/turno e o evento de desvio.

**Rationale:** permite auditoria comportamental e diagnóstico do checkpoint sem
duplicar dados pessoais em observabilidade técnica.

**Alternatives considered:** registrar os textos brutos no log para facilitar
debug. Rejeitada por duplicação desnecessária de dados pessoais.

## Decision 9 — reconciliar o baseline antes de novos escritores

**Decision:** as alterações já existentes na árvore de trabalho são tratadas
como baseline não verificado. Antes de delegar implementação, o Tech Lead deve
inventariar o delta, associá-lo aos requisitos/tarefas, executar testes
proporcionais e classificar cada slice como aproveitável, incompleto,
conflitante ou fora de escopo. A mera presença de código ou teste não conclui
uma tarefa.

**Rationale:** a branch atual contém mudanças extensas em backend, SOUL, plugin,
POC, compose e testes, enquanto os artefatos anteriores descreviam uma execução
greenfield. Recomeçar as tarefas poderia duplicar trabalho ou apagar decisões
já materializadas; presumir conclusão esconderia regressões ainda não
verificadas.

**Alternatives considered:** marcar todas as tarefas aparentes como concluídas
ou descartar o diff e reimplementar. Rejeitadas, respectivamente, por falta de
evidência e por risco destrutivo sobre alterações preexistentes.

## Evidências consultadas

- `specs/008-complete-urba-service-flow/spec.md`
- `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/CommercialPolicyService.java`
- `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/tools/StatefulDomainToolService.java`
- `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/interfaces/rest/poc/DomainToolController.java`
- `apps/urbana-connect-api/src/main/java/br/com/urbana/connect/application/reception/ReceptionOrchestrator.java`
- `integrations/hermes-agent/plugins/urbana-domain/tools.py`
- `docs/specs/pee-102-retomada-tecnica-humano-urba-hermes.md`
- `script-example.log` e registros Mongo da execução manual de 2026-08-17
- entrevista de refinamento técnico conduzida com Emanuel até 2026-08-26

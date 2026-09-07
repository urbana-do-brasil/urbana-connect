# Pacote de decisão — Fase 0 do piloto de memória com Obsidian

## Estado e limite deste documento

- **Status:** aprovação explícita registrada; item 2 **Superseded/waived by Emanuel**; itens 0–9 concluídos; item 10 **GO**; **Fase 1: verified**.
- **Fase 0:** pacote de decisão com aprovação registrada e ajuste de armazenamento interno; isso não autoriza acesso do Codex ou escrita no vault.
- **Fase 1:** **verified**. O vault foi registrado e aberto no Obsidian por Emanuel; a estrutura visual foi confirmada, as validações automatizadas passaram e os controles locais permanecem restritos.
- **Fase 2:** **implemented/verified** somente para consulta de leitura restrita; a escrita continua bloqueada por gate separado.
- **Escopo deste pacote:** registrar a aprovação, as evidências locais, a renúncia explícita ao backup independente e os itens da checklist; a aprovação não amplia autorização para dados negados, acesso do Codex ou escrita.
- **Evidência manual concluída:** Emanuel confirmou a abertura em 2026-08-04 e forneceu captura do Obsidian exibindo o vault `urbana-connect-memory` e sua estrutura. O estado local registra o vault como `registered=true` e `open=true`; Sync e Publish estão `false`, não há plugin comunitário nem diretório de plugins.

O contrato macro continua no [plano de memória durável com Obsidian](./obsidian-memory-and-orchestration.md). As regras de execução e entrega do repositório continuam em [Princípios de Engenharia da Urba](../engineering-principles.md). Este pacote aplica também os gates, separação de papéis, autonomia graduada, fontes oficiais e evidência independente descritos no [plano de orquestração do Codex](./codex-engineering-orchestrator.md).

## Como ler as decisões

As etiquetas abaixo evitam que uma observação seja confundida com autorização:

| Etiqueta | Significado | Pode autorizar execução? |
| --- | --- | --- |
| **Fato verificado** | Resultado observável da inspeção local, datado e com escopo limitado. | Não. É evidência, não decisão. |
| **Default técnico aprovado** | Opção aceita para o piloto, reversível quando indicado. | Não. A aprovação não autoriza execução sem o gate correspondente. |
| **Aprovação de negócio necessária** | Escolha sobre dados, escopo, retenção, backup, compartilhamento ou autorização que ainda venha a ser alterada. | Somente após resposta explícita. |
| **Gate** | Condição que precisa de evidência antes de avançar. | Não pode ser inferido por silêncio. |

## Fatos verificados localmente — baseline

Inspeção de baseline realizada em 2026-08-04, antes da execução da Fase 1 e
sem alterar o estado do sistema:

1. O Obsidian não foi encontrado nos locais comuns inspecionados.
2. O caminho anteriormente proposto e o vault correspondente não existem.
3. Não foi encontrada skill `memory-vault`, servidor MCP de memória ou regra de roteamento disponível para o vault.
4. FileVault está ativo.
5. `tmutil destinationinfo` não informa destino do Time Machine; portanto não há backup de restauração comprovado para este piloto.
6. O uso e a geração da memória nativa do Codex permanecem desligados por padrão nesta instalação; não foi encontrado um parâmetro de configuração de memória que os habilite.
7. A inspeção de armazenamento não encontrou destino ou backup do Time Machine, snapshot local de backup utilizável ou disco físico externo; há somente o disco interno `disk0` e o disco APFS sintetizado `disk1`/snapshot raiz no mesmo SSD.
8. `/Volumes` contém apenas `Macintosh HD -> /`, há aproximadamente 3,2 GiB livres e nenhum backup ou restauração foi tentado.

Esses fatos registram que não havia backup independente no momento da inspeção.
FileVault protege a confidencialidade do SSD, mas não a recuperação após perda ou
corrupção. A decisão posterior de Emanuel aceita explicitamente esse risco no
piloto e torna **Superseded/waived by Emanuel** o gate de destino distinto e
restauração; cópias no mesmo disco e Git local não são backup.

## Evidências de execução da Fase 1 — 2026-08-04

As evidências abaixo registram o estado após a implementação local e a
confirmação visual de Emanuel:

1. O Obsidian oficial foi instalado pelo Homebrew cask, versão `1.13.4`, em
   `/Applications`, ocupando aproximadamente `514 MB`; restam cerca de `2,5
   GiB` no SSD, acima do limiar operacional de `1 GiB`.
2. O vault exato foi criado em
   `~/Knowledge/Urba/urbana-connect-memory`; a raiz e os diretórios têm modo
   `0700`, os arquivos têm modo `0600` e não há symlinks.
3. A estrutura é Urba-only e contém README, política, schema, templates,
   índice, projeto, pesquisa e cinco notas sanitizadas. A validação de máquina
   encontrou 10 notas YAML com 10 IDs únicos, `sources` preenchido, 13 wiki
   links e varreduras de segredo/PII aprovadas.
4. A proveniência foi corrigida: notas de repositório/pesquisa mantêm
   `confirmed_by`/`confirmed_at` nulos; notas humanas estão atribuídas a
   Emanuel.
5. O Git local contém os commits `33d6c33` e `a952169`, está limpo, não possui
   remoto e tem `.git` endurecido (`0700`/`0600`). Esse Git no mesmo SSD é
   auditoria, não backup.
6. O Obsidian criou `.obsidian` ao registrar o vault. A inspeção confirmou
   `Sync=false`, `Publish=false`, ausência de `community-plugins.json` e de
   diretório de plugins; `.obsidian` permanece ignorado pelo Git e endurecido
   em `0700`/`0600`.
7. Emanuel confirmou a abertura e forneceu captura mostrando o vault e sua
   estrutura. O estado local do Obsidian registra o caminho exato com
   `registered=true` e `open=true`.

## Registro de aprovação e auditoria

- **Data/hora:** 2026-08-04 07:55:07 -03 (America/Fortaleza).
- **Fonte:** current Codex task (mensagem do usuário).
- **Aprovador:** Emanuel.
- **Desvios declarados:** nenhum.
- **Limite autorizado no registro histórico:** registrar as decisões (item 1) e escolher um destino criptografado distinto e concluir o teste de restauração (item 2); instalação, criação do vault, configuração, acesso do Codex e demais itens continuavam bloqueados até a decisão posterior abaixo.

O texto abaixo é um registro histórico da aprovação recebida. A exigência de
destino externo/distinto e restauração nele contida foi posteriormente
superseded/waived para este piloto pela decisão registrada a seguir; os demais
limites continuam válidos:

```text
Eu, Emanuel, aprovo o bundle proposto no documento docs/plans/obsidian-phase-0-decision-packet.md para um piloto somente da Urba e do projeto urbana-connect, com memória pessoal futura em vault fisicamente separado. Aprovo o caminho ~/Knowledge/Urba/urbana-connect-memory, permissões 0700, armazenamento local sem Sync/Publish/plugins comunitários/embeddings/Git remoto, memória nativa do Codex e Chronicle desligados, acesso do Codex ausente na Fase 1, leitura restrita somente na Fase 2 e escrita somente após gate separado. Aprovo a política deny-by-default para sensibilidade, o schema delta e os prazos de revisão/retenção propostos. Autorizo, na Fase 2 e somente após o GO da Fase 1, o processamento pelo Codex/OpenAI de trechos mínimos classificados como internos não sensíveis para recuperação de contexto; não autorizo o processamento das classes negadas. Autorizo iniciar somente os itens 1 e 2 da checklist para registrar as decisões, escolher um backup criptografado em destino distinto e concluir o teste de restauração. A instalação do Obsidian, a criação do vault e os demais itens permanecem bloqueados até a evidência desse teste; não autorizo ações fora da checklist.
```

### Decisão posterior — armazenamento interno e risco de perda aceito

- **Data:** 2026-08-04.
- **Fonte:** current Codex task (mensagem do usuário).
- **Aprovador:** Emanuel.
- **Decisão:** retirar a necessidade de disco externo ou outro destino
  independente; armazenar o piloto somente no SSD interno, em
  `~/Knowledge/Urba/urbana-connect-memory`.
- **Efeito:** o item 2 da checklist e o gate de backup/restauração ficam
  **Superseded/waived by Emanuel**. Não existe backup independente para este
  piloto e o risco de perda de dados é explicitamente aceito. FileVault protege
  confidencialidade, não recuperação; cópias no mesmo disco e Git local não são
  backup.
- **Intenção exata registrada:** “Retire essa parte da necessidade de um disco
  externo. Armazene no disco interno mesmo. Estamos burocratizando demais essa
  atividade. Eu Emanuel, autorizo esse ajuste no plano”.

O ajuste não libera Sync, Publish, plugins comunitários, embeddings ou Git
remoto, não mistura o vault com memória pessoal, não dá acesso ao Codex na Fase
1, não autoriza escrita na Fase 2 e não libera classes de dados negadas. A
leitura restrita da Fase 2 continua condicionada ao GO da Fase 1, e a escrita
continua condicionada a gate separado.

## Bundle técnico aprovado (execução permitida conforme gates restantes)

O bundle abaixo é o conjunto aprovado para o piloto de `urbana-connect`. A
aprovação registra as decisões, mas não substitui os gates restantes nem atesta
que a implantação já ocorreu.

- **Escopo:** piloto somente da Urba e do projeto `urbana-connect`; memória pessoal deverá usar, no futuro, um vault fisicamente separado.
- **Localização:** `~/Knowledge/Urba/urbana-connect-memory`, fora do repositório e de pastas de sincronização/cloud, diretório com modo `0700` e sem symlinks; criação e permissões foram verificadas por máquina.
- **Instalação:** Obsidian cask oficial `1.13.4` instalado em `/Applications`; o aplicativo inicia. O registro visual do vault na GUI foi confirmado por Emanuel.
- **Operação:** armazenamento local; sem Obsidian Sync, Publish, plugins comunitários, Git remoto ou embeddings durante o piloto.
- **Auditoria:** Git local criado sem remoto, com commits `33d6c33` e `a952169`, estado limpo e `.git` em `0700`/`0600`; estado transitório do Obsidian, índices e caches permanecem excluídos. Esse Git no mesmo SSD não é backup.
- **Acesso do Codex:** nenhuma integração na Fase 1; leitura restrita somente na Fase 2; escrita somente na Fase 3, após um gate separado.
- **Memórias concorrentes:** memória nativa do Codex (uso e geração) e Chronicle permanecem desligados durante o piloto.
- **Busca:** propriedades e busca lexical; busca semântica/embeddings só após avaliação demonstrar falha real e após novo gate.
- **Conteúdo permitido inicialmente:** conteúdo público, com origem e referência verificáveis; conteúdo interno não sensível só poderá ser processado pelo Codex/OpenAI na Fase 2 após aprovação explícita de Emanuel, com minimização dos trechos recuperados.
- **Conteúdo negado por padrão:** segredos, tokens, chaves, credenciais, dados pessoais ou de clientes, dados regulados/sensíveis e informação empresarial confidencial. Uma classe só pode ser liberada por política explícita posterior.
- **Revisão e retenção aprovadas:** pesquisa revisada em 30 dias; preferência/decisão em 180 dias; memória `proposed` revisada em 7 dias e eliminada em 30 dias se não for promovida ou justificada.
- **Armazenamento e recuperação:** somente o SSD interno protegido por FileVault; não há backup independente nem teste de restauração exigidos no piloto. O risco de perda de dados é explicitamente aceito; FileVault protege confidencialidade, não recuperação, e cópias no mesmo disco/Git local não são backup.

## Decisões registradas e pendências remanescentes

As decisões abaixo foram aprovadas no registro histórico e atualizadas pela
decisão de armazenamento interno. Não há bloqueio operacional de backup; os
itens 3+ têm evidências próprias registradas na checklist e na seção de
execução da Fase 2:

1. **Aprovado:** escopo Urba-only e separação física de uma futura memória pessoal.
2. **Aprovado:** `~/Knowledge/Urba/urbana-connect-memory`, modo `0700`, fora de repositório/cloud e sem symlink.
3. **Aprovado:** operação local sem Sync, Publish, plugins comunitários, Git remoto ou embeddings no piloto.
4. **Superseded/waived by Emanuel:** a exigência de backup criptografado em destino distinto e restauração testada não se aplica a este piloto. O armazenamento é somente interno, não há backup independente e o risco de perda foi aceito; FileVault não é mecanismo de recuperação e cópias no mesmo disco/Git local não são backup.
5. **Aprovado:** Git local somente para auditoria após gate, sempre excluindo estado transitório, índices e caches; sem remoto.
6. **Aprovado:** sequência de acesso `sem acesso → leitura restrita → escrita sob novo gate`.
7. **Aprovado:** memória nativa do Codex e Chronicle desligados no piloto.
8. **Aprovado:** lista de conteúdo permitido/negado e regra deny-by-default para sensibilidade.
9. **Aprovado com gate:** na Fase 2, somente após o GO da Fase 1, trechos mínimos `internal-nonsensitive` podem ser processados pelo Codex/OpenAI; classes negadas não são autorizadas.
10. **Aprovado:** schema delta e períodos de revisão/retenção.
11. **Aprovado:** nenhuma nota do vault será tratada como autorização, estado vivo ou substituto de Jira, GitHub, specs, `AGENTS.md` ou secret manager.

O item 2 não bloqueia mais a instalação ou a criação do vault. Não é necessário
escolher, formatar, criptografar ou configurar um destino externo/Time Machine
para este piloto. Uma futura mudança de risco ou escopo poderá reabrir a
decisão de recuperação, mas exigirá novo registro explícito.

## Matriz de decisão

| Decisão | Bundle aprovado / default técnico | Racional | Reversibilidade | Ação/aceite de Emanuel |
| --- | --- | --- | --- | --- |
| Escopo do piloto | Urba-only; vault pessoal futuro separado fisicamente | reduz mistura de identidades, públicos e políticas | alta: separar ou encerrar o piloto sem migrar notas | **Aprovado** |
| Caminho e permissões | `~/Knowledge/Urba/urbana-connect-memory`, `0700`, sem symlink | menor privilégio e isolamento do repo/cloud | média: mover o vault em Markdown, com validação de links | **Aprovado; criação, permissões e registro GUI verificados** |
| Sincronização/publicação | local apenas; Sync, Publish e plugins comunitários desligados | reduz superfície de exposição, conflito e código de terceiros | alta: habilitar apenas por decisão e revisão posteriores | **Aprovado; inventário/configuração sem esses componentes verificado** |
| Versionamento | Git local sem remoto; excluir `.obsidian` transitório, índices e caches | auditabilidade sem transformar cache em fonte canônica | alta: remover `.git` sem perder Markdown | **Implementado; commits `33d6c33`/`a952169`, limpo; não é backup** |
| Acesso do Codex | nenhum na Fase 1; leitura na Fase 2; escrita na Fase 3 com gate | separa fundação, recuperação e mutação | alta: revogar acesso e apagar a integração | **Aprovado; integração ausente na Fase 1** |
| Memória nativa/Chronicle | desligados no piloto | evita duas fontes concorrentes e ambiguidades de precedência | alta: reavaliar em fase própria | **Aprovado; ausência verificada** |
| Busca | lexical + propriedades | simples, transparente e reconstruível | alta: adicionar índice semântico depois | **Aprovado** |
| Dados e processamento | público por padrão; interno não sensível somente com aprovação explícita para processamento mínimo pelo Codex/OpenAI na Fase 2; deny-by-default | reduz risco de segredo, privacidade, confidencialidade e egresso não autorizado | alta: revogar acesso futuro e restringir novamente a público | **Aprovado com gate da Fase 1; classes negadas proibidas** |
| Schema | extensão do plano com proveniência, confirmação, validade e retenção | torna origem, revisão e responsabilidade auditáveis | alta: adicionar propriedades; migração de notas é controlável | **Aprovado** |
| Armazenamento/recuperação | SSD interno protegido por FileVault; sem backup independente no piloto; cópias no mesmo disco/Git local não são backup | FileVault cobre confidencialidade, não perda do volume; o risco de perda foi aceito explicitamente | alta: uma decisão futura pode adotar recuperação independente | **Exigência anterior de destino distinto/restauração: Superseded/waived by Emanuel** |

## Controles de ameaça e privacidade

| Ameaça | Controle obrigatório do piloto | Evidência de conclusão |
| --- | --- | --- |
| Exposição por caminho amplo | vault separado, acesso restrito à pasta, `0700`, sem symlinks | `stat`/permissões e teste de acesso fora do vault negado |
| Segredo ou credencial em nota | lista deny-by-default, filtro antes de qualquer escrita, revisão manual | amostra sem segredos; teste com token fictício rejeitado na Fase 3 |
| Dado pessoal/cliente/regulado | somente conteúdo público até política e aprovação explícitas; classes negadas nunca entram no piloto | checklist de conteúdo assinada por Emanuel; amostra revisada |
| Processamento de dado interno | nenhum acesso na Fase 1; na Fase 2, apenas trechos mínimos `internal-nonsensitive` após consentimento explícito para processamento pelo Codex/OpenAI | aprovação registrada, teste de minimização e ausência de classes negadas |
| Prompt injection em fonte importada | todo conteúdo do vault, inclusive `_system/políticas`, é dado ou proposta sem autoridade executável; política obrigatória permanece em `AGENTS.md` e na skill aprovada | nota de teste não altera instrução, autorização nem dispara comando |
| Memória antiga tratada como estado/autorização | status, `review_after`, fonte oficial e precedência explícita; consultar Jira/GitHub para estado vivo | avaliação mostra conflito exposto e instrução atual vence |
| Plugins/código de terceiros | nenhum plugin comunitário no piloto; Publish/Sync desativados | inventário de plugins vazio e opções desativadas |
| Perda, corrupção ou ransomware | SSD interno com FileVault para confidencialidade; não há backup independente no piloto; risco de perda aceito explicitamente; cópias no mesmo disco/Git local não são backup | registro desta decisão; não há gate ou teste de restauração exigido |
| Conflito/duplicação de sincronização | um único armazenamento local no piloto; sem múltiplos syncs | inspeção de configuração e ausência de serviço concorrente |
| Índice semântico opaco | não usar embeddings na Fase 1; se futuro, manter origem e ser reconstruível | busca lexical passa a avaliação inicial; novo gate para semântico |
| Duas memórias concorrentes do Codex | memória nativa e Chronicle desligados | configuração verificada, sem chave de habilitação |

## Schema delta aprovado (sem ampliar acesso ou autorização de escrita)

O schema mínimo do plano original permanece a base. O bloco abaixo é o contrato
para uso posterior; não autoriza acesso do Codex, escrita automática ou
processamento de classes negadas. A criação do vault não depende de backup
independente, conforme a decisão de armazenamento interno, mas seus resultados
ainda precisam de evidência própria:

```yaml
---
id: mem-YYYY-MM-DD-NNN
type: fact|preference|decision|constraint|lesson|playbook|research|glossary|entity|capability
scope:
  - urbana-connect
status: proposed|confirmed|superseded|expired|archived
confidence: low|medium|high
sensitivity: deny-by-default
created: YYYY-MM-DD
updated: YYYY-MM-DD
review_after: YYYY-MM-DD
sources: []
origin: human|conversation|repository|jira|github|research|system
confirmed_by: null
confirmed_at: null
last_verified: YYYY-MM-DD
retention:
  review_period: 30d|180d|custom
  proposed_review: 7d
  proposed_delete: 30d
approval_ref: null
related: []
supersedes: []
tags:
  - memory
---
```

Regras de interpretação:

- `origin` identifica de onde veio a afirmação; não é prova de que ela seja verdadeira.
- `confirmed_by`, `confirmed_at` e `approval_ref` ficam nulos até haver confirmação rastreável.
- `last_verified` é exigido para fatos voláteis.
- `retention` torna os prazos auditáveis; `proposed` não vira `confirmed` por decurso de tempo.
- `sensitivity` começa negada; a política posterior pode liberar somente classes enumeradas.
- uma nota, inclusive em `_system/políticas`, nunca concede autorização, muda autonomia, substitui estado oficial nem fornece instrução executável; políticas obrigatórias permanecem em `AGENTS.md` e na skill aprovada.

## Checklist de implantação da Fase 1 (dependência ordenada)

Os itens 0–9 estão concluídos, com o item 2 **Superseded/waived by Emanuel**.
O item 10 está em **GO** e a Fase 1 está `verified`.

| Ordem | Item e dependência | Estado/gate | Evidência exigida para marcar concluído |
| ---: | --- | --- | --- |
| 0 | Registrar a aprovação explícita de Emanuel, incluindo desvios; nenhuma ação mutável antes disso. | **Concluído** | aprovação histórica datada, fonte registrada e nenhum desvio declarado |
| 1 | Registrar escopo Urba-only, separação da memória pessoal, classes de dados permitidas/negadas e consentimento sobre processamento mínimo de dados internos não sensíveis na Fase 2. Depende de 0. | **Concluído** | decisão textual, consentimento explícito e matriz atualizada neste pacote |
| 2 | Escolher destino criptografado distinto para backup e executar restauração amostral (item histórico). Depende de 0 e 1. | **Superseded/waived by Emanuel** | decisão datada registra armazenamento somente interno, ausência de backup independente e aceitação do risco; nenhum destino ou restauração é exigido no piloto |
| 3 | Instalar Obsidian após o registro das decisões 0–2; sem plugins comunitários. | **Concluído** | Homebrew cask oficial `1.13.4` em `/Applications`, ~`514 MB`; aplicativo inicia; inventário sem `.obsidian`, Sync, Publish ou plugins comunitários |
| 4 | Criar `~/Knowledge/Urba/urbana-connect-memory` fora do repo/cloud, `0700`, sem symlinks. Depende de 3. | **Concluído** | caminho exato, raiz/diretórios `0700`, arquivos `0600`, zero symlink; validação por máquina |
| 5 | Criar diretórios, `_system`, política, templates e schema delta; manter Markdown como fonte canônica. Depende de 4. | **Concluído** | estrutura Urba-only com README, política, schema, templates, índice, projeto e pesquisa; Markdown/YAML válidos |
| 6 | Criar índice de `urbana-connect` e registrar pesquisa inicial com referências; não copiar documentos oficiais integralmente. Depende de 5. | **Concluído** | índice/projeto/pesquisa e referências verificáveis; 13 wiki links encontrados |
| 7 | Adicionar poucas memórias públicas/internas não sensíveis e revisá-las manualmente. Depende de 1 e 5–6. | **Concluído** | cinco notas sanitizadas; 10 notas YAML/10 IDs únicos, `sources`, provenance, secret/PII scan PASS; estrutura visual confirmada por Emanuel |
| 8 | Se aprovado, inicializar Git local de auditoria, ignorando estado transitório, índices e caches; sem remoto. Depende de 0, 4 e 5. | **Concluído (opcional)** | commits `33d6c33` e `a952169`, `git status` limpo, sem remote, `.git` `0700`/`0600`; Git local não é backup |
| 9 | Validar abertura no Obsidian, propriedades, busca lexical, correção manual e limites de conteúdo. Depende de 2–8. | **Concluído** | captura de Emanuel mostra o vault aberto; estado local `registered=true`/`open=true`; Sync/Publish `false`, sem plugins comunitários; validações de conteúdo passaram |
| 10 | Tech Lead aceita somente com todas as evidências e registra `GO` para a Fase 2 (leitura); escrita continua fora de escopo. Depende de 9. | **GO / Fase 1 verified** | critérios atendidos; Fase 2 pode implementar somente consulta de leitura restrita; escrita continua em gate separado |

### Critério global de GO da Fase 1

O GO foi registrado porque: (a) a aprovação explícita, já registrada
acima, continuar válida; (b) o vault abrir e os arquivos Markdown/propriedades
forem válidos; (c) a amostra não contiver classe proibida; (d) os controles de
plugins, Sync, Publish, embeddings e acesso do Codex forem verificáveis; e (e)
Emanuel conseguir localizar e corrigir uma memória. A ausência de backup
independente não impede o GO da Fase 1, pois esse risco foi aceito explicitamente
e não é gate deste piloto. Emanuel confirmou o vault aberto, e a inspeção local
confirmou os controles desativados. A Fase 1 está `verified` e a Fase 2 recebe
GO somente para implementar leitura restrita; escrita permanece fora de escopo.

O registro histórico da aprovação está na seção “Registro de aprovação e auditoria”; não há novo bloco de resposta pendente. Qualquer mudança futura de escopo ou destino deverá ser registrada como nova decisão explícita e não pode ser inferida deste pacote.

## Evidências de execução da Fase 2 — 2026-08-04

A Fase 2 foi implementada e verificada dentro do escopo aprovado:

1. A skill global `memory-vault` foi criada pelo scaffold oficial em
   `~/.codex/skills/memory-vault`, com `SKILL.md`, `agents/openai.yaml`, política
   de recuperação, script lexical somente leitura e testes automatizados.
2. A busca é determinística, accent-insensitive, com limite padrão 5/teto 10,
   snippets numerados e caminhos relativos; os testes cobrem match confirmado,
   propostas explícitas, scope/sensitivity/status/expiry, stale, limite,
   symlink, segredo, snippets e CLI sem root configurável.
3. `.codex/config.toml` local seleciona `default_permissions =
   "urbana-memory-read"`; o perfil estende `:workspace` e concede `read`
   somente ao caminho absoluto do vault. Não há `sandbox_mode` legado, rede,
   Sync, Publish, plugins comunitários ou embeddings adicionados.
4. `AGENTS.md` recebeu somente uma regra curta de roteamento para
   `$memory-vault`, preservando o arquivo preexistente e mantendo memória como
   contexto não autorizativo; classes negadas permanecem proibidas.
5. A auditoria antes/depois registrou `git status` e hash do vault inalterados;
   nenhuma escrita, exportação ou rede foi realizada durante a consulta.

A aprovação desta fase continua limitada ao processamento mínimo de trechos
`internal-nonsensitive` para recuperação de contexto. Não autoriza classes
negadas, escrita no vault, memória nativa do Codex, Chronicle ou qualquer ação
fora dos gates; escrita exige gate separado da Fase 3.

## Referências

- [Plano de memória durável com Obsidian](./obsidian-memory-and-orchestration.md)
- [Princípios de Engenharia da Urba](../engineering-principles.md)
- [Plano do Codex Engineering Orchestrator](./codex-engineering-orchestrator.md)
- [Codex — Customization](https://developers.openai.com/codex/concepts/customization)
- [Codex — Memories](https://learn.chatgpt.com/docs/customization/memories)
- [Codex — Model Context Protocol](https://learn.chatgpt.com/docs/extend/mcp)
- [Model Context Protocol — Understanding MCP clients](https://modelcontextprotocol.io/docs/learn/client-concepts)

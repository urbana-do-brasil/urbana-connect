# AGENTS.md

Este arquivo define como os agentes de IA operam no repositório `urbana-connect`.

Objetivo:

- deixar explícito o papel de cada agente
- reduzir ambiguidade de execução
- alinhar especificação, implementação, testes, review e transições operacionais

## Agentes

### Visão Claude

Papel principal:

- especificação
- arquitetura
- refinamento de solução
- implementação delicada quando o problema exigir mais desenho que volume
- review crítico orientado a escopo, coerência e riscos

Responsabilidades típicas:

- estruturar a spec da feature
- propor abordagem técnica
- desafiar inconsistências no desenho
- revisar PRs contra a intenção original da entrega

### Visão Codex

Papel principal:

- execução pesada
- implementação incremental
- criação e ajuste de testes
- validação operacional
- fechamento de PR e documentação objetiva do que foi entregue

Responsabilidades típicas:

- transformar a spec em código e artefatos concretos
- escrever ou ajustar testes
- validar build, CI/CD e comportamento em homolog
- atualizar Jira e GitHub conforme o fluxo acordado

## Modelo de autonomia

O projeto usa três níveis de autonomia:

| Nível | Significado |
| --- | --- |
| `A` | baixo risco, pode executar sem pedir |
| `B` | risco moderado, confirmar antes |
| `C` | alto risco, aprovação explícita |

Aplicação prática:

- `A`
  - leitura de arquivos
  - exploração do repositório
  - edição local
  - criação de branch
  - commits
  - criação e atualização de documentação
  - comentários em Jira e GitHub ligados ao trabalho em andamento
- `B`
  - criação de novas stories/tasks/subtasks no Jira
  - transições de status com impacto de fluxo
  - PRs relevantes para homolog
  - mudanças com impacto operacional claro
- `C`
  - deploy em produção
  - alteração de permissões ou credenciais sensíveis
  - ações destrutivas
  - comunicação externa em nome da Urba

Mesmo com autonomia:

- nenhum agente deve assumir decisão de negócio implícita
- ações públicas devem ser rastreáveis
- Jira e GitHub são registros oficiais do trabalho

## Ciclo SDD + TDD

Antes de cada feature de negócio:

1. criar ou revisar a spec
2. definir comportamento esperado, critérios de aceite e edge cases
3. escrever os testes antes da implementação quando houver comportamento novo
4. só então implementar

Distribuição preferencial do ciclo:

1. `Spec`
   - Visão Claude lidera a escrita ou revisão da spec
   - Visão Codex pode complementar com exemplos mais concretos
2. `Test First`
   - Visão Codex escreve ou ajusta os testes que descrevem o comportamento
   - os testes devem falhar antes da implementação nova
3. `Implementation`
   - Visão Codex implementa a solução mínima para os testes passarem
   - Visão Claude pode entrar quando houver decisão arquitetural ou refactor sensível
4. `Review`
   - Visão Claude revisa aderência à spec e coerência arquitetural
   - Visão Codex pode revisar riscos de regressão, implementação e testes
5. `Refactor`
   - só com testes verdes
   - sem perder aderência à spec

Regra central:

- a spec define o contrato macro
- os testes definem o contrato executável micro
- o review deve validar implementação contra a spec, não só contra estilo

## Fluxo de branches

Fluxo padrão do projeto:

- `feature/* -> hml -> validação em homolog -> main`

Papéis das branches:

- `hml`
  - branch de integração e validação em homolog
  - recebe features primeiro
  - é a base para teste funcional e operacional
- `main`
  - branch mais protegida e mais estável
  - só deve receber o que já foi validado em homolog

Regras:

- novas features devem sair da `hml`
- PRs iniciais de feature devem voltar para `hml`
- depois da validação em homolog, abre-se a promoção para `main`

## Fluxo de Pull Request

Todo PR deve ter:

- objetivo claro
- resumo do que mudou
- validação executada
- referência ao ticket Jira

Fluxo esperado:

1. abrir branch de trabalho
2. implementar o escopo da subtarefa
3. validar o que for relevante
4. abrir PR
5. registrar o PR no Jira
6. mover a issue para `Awaiting approval`
7. aguardar revisão/aprovação humana

Nenhum agente deve considerar trabalho concluído apenas porque o código foi escrito. PR e rastreabilidade fazem parte da entrega.

## Fluxo de Jira

Para subtarefas e tasks da Urbana:

1. ao iniciar a execução
   - mover para `Em andamento`
2. ao finalizar a implementação e abrir PR
   - comentar no Jira com o link do PR
   - mover para `Awaiting approval`
3. após aprovação e merge
   - mover para `Concluído`
   - identificar a próxima subtarefa da fila
   - confirmar continuação com Emanuel quando necessário

## Princípios de trabalho

- preferir clareza a esperteza
- reduzir escopo implícito
- não esconder risco técnico
- deixar validação explícita
- documentar decisões quando elas forem reutilizáveis

Arquivos de referência do fluxo:

- `docs/engineering-principles.md`
- `docs/specs/_template.md`
- `docs/specs/exemplo-webhook.md`

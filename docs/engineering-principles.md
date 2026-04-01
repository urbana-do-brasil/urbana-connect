# Princípios de Engenharia da Urba

Este documento formaliza os princípios que já orientam o desenvolvimento do `urbana-connect`. Ele serve como referência para engenharia humana e para agentes de IA antes da implementação de features de negócio.

## Objetivo

- manter consistência técnica e operacional
- reduzir ambiguidade na implementação e no review
- tornar explícitos os critérios mínimos de qualidade e entrega

## Stack oficial

As decisões de stack do projeto não devem ser revistas casualmente em cada feature. O baseline atual é:

| Área | Padrão adotado |
| --- | --- |
| Linguagem | Java 21 LTS |
| Framework | Spring Boot 3.4.x |
| Build | Gradle 8.x |
| Testes | JUnit 5 + Spring Boot Test + Testcontainers |
| Persistência atual | MongoDB |
| Containerização | Docker multi-stage |
| Runtime de homolog | k3s em VPS Contabo |
| Manifestos | Kustomize (`base` + `overlays`) |
| Observabilidade inicial | Prometheus, Grafana, Loki e Promtail |
| Registry | GHCR (`ghcr.io`) |

Implicações práticas:

- novas dependências devem respeitar essa base antes de introduzir novas peças de infraestrutura
- upgrades de versão devem ser intencionais e justificados
- mudanças de stack exigem decisão explícita, não devem entrar como detalhe incidental de subtarefa

## Arquitetura

O projeto segue Clean Architecture como padrão organizacional principal.

Camadas esperadas:

- `domain`: entidades e contratos centrais, sem dependências externas
- `application`: casos de uso, orquestração e configuração da aplicação
- `infrastructure`: integrações e implementações concretas
- `interfaces`: pontos de entrada e saída expostos ao mundo externo

Regras:

- regras de negócio não devem nascer em controllers
- dependências externas devem ser isoladas nas bordas
- casos de uso devem ser orientados por comportamento, não por detalhes de framework
- features novas devem preservar essa separação em vez de "furar" camadas por conveniência

## Qualidade e testes

O projeto adota qualidade automatizada como requisito de entrega, não como etapa opcional.

Padrões atuais:

- testes automatizados fazem parte do fluxo normal de implementação
- JaCoCo com cobertura mínima de linha em `60%`
- o build falha quando o threshold mínimo não é atendido
- testes de integração com infraestrutura real devem usar Testcontainers quando fizer sentido

Diretrizes:

- mudanças comportamentais devem vir acompanhadas de testes
- correções de bug idealmente devem reproduzir a falha em teste
- cobertura não substitui testes bons; ela é um piso, não a definição de qualidade
- configuração, wiring e exceções podem ter tratamento diferente no cálculo de cobertura, mas isso precisa permanecer explícito no build

## Configuração e segredos

O projeto usa configuração por ambiente e separação clara entre código e segredos.

Regras:

- secrets não são commitados no repositório
- valores sensíveis vivem no cluster, no GitHub Actions ou em arquivos locais fora de versionamento
- templates podem ser versionados; valores reais, não
- qualquer mudança operacional deve preservar o princípio de menor privilégio

## Autonomia dos agentes

O modelo operacional atual usa autonomia graduada para humanos e agentes de IA:

| Nível | Regra |
| --- | --- |
| `A` | Baixo risco: pode executar sem pedir |
| `B` | Moderado: confirmar antes |
| `C` | Alto risco: aprovação explícita |

Aplicação prática:

- `A`: leitura, exploração, edição local, commits, comentários em Jira sem transição de negócio
- `B`: criação de stories, transição de status relevante, PR/deploy para homolog quando houver impacto operacional relevante
- `C`: deploy em produção, permissões, credenciais, ações destrutivas e comunicação externa

Mesmo com autonomia alta:

- agentes não devem assumir intenção de negócio não explicitada
- qualquer ação pública ou destrutiva exige cautela extra
- o Jira e o GitHub são os registros formais do trabalho, não apenas o chat

## Convenções de commit

O histórico atual do projeto segue Conventional Commits enxutos, normalmente com referência da PEE.

Padrão recomendado:

- `feat(PEE-xx): ...`
- `fix(PEE-xx): ...`
- `docs(PEE-xx): ...`
- `chore(PEE-xx): ...`

Regras:

- a mensagem deve descrever a mudança principal, não a intenção vaga
- quando houver ticket associado, ele deve aparecer no escopo ou no corpo da PR
- commits de automação operacional podem usar `chore`
- evitar commits genéricos como `ajustes`, `wip` ou `correções diversas`

## Convenções de Pull Request

Pull Request é o ponto formal de validação da entrega.

Cada PR deve, no mínimo:

- deixar claro o objetivo da mudança
- resumir o que foi alterado
- listar a validação executada
- apontar o ticket Jira relacionado

Expectativas de fluxo:

- implementação concluída -> PR aberto
- PR comentado no Jira
- issue movida para `Awaiting approval`
- merge apenas após revisão/aprovação humana

Code review deve validar:

- aderência ao escopo
- aderência à arquitetura
- riscos de regressão
- cobertura por testes
- impacto operacional

## Branches e deploy

O projeto já opera com uma distinção clara entre desenvolvimento e deploy de homolog.

Regras atuais:

- `main` é a branch mais protegida e representa o estado mais estável do produto
- `hml` é a branch de integração e validação em homolog
- homolog é o ambiente para teste funcional e operacional antes de promover mudanças para o ramo mais protegido
- o workflow de deploy publica imagem versionada no GHCR e aplica a tag em homolog a partir da `hml`
- a própria `hml` passa a refletir a tag efetivamente implantada em homolog

Implicações:

- features devem ser validadas em `hml` antes de serem consideradas prontas para promoção ao ramo mais protegido
- `main` não deve ser usada como branch de experimento ou validação de homolog
- não usar `latest` como base operacional de deploy
- deploy em homolog deve ser rastreável por SHA/tag
- mudanças de aplicação e de infraestrutura precisam preservar esse modelo

## Definição mínima de pronto

Uma entrega só deve ser considerada pronta quando:

- o escopo acordado estiver implementado
- os testes relevantes tiverem sido executados
- o impacto operacional estiver entendido
- o PR estiver aberto com contexto suficiente para revisão
- o Jira refletir o estado real da execução

## Relação com SDD e TDD

Estes princípios são a fundação para o próximo passo do projeto:

- SDD: especificar antes de implementar
- TDD: escrever teste antes do código de comportamento novo

Ou seja:

- este documento define as regras estáveis do terreno
- specs futuras definirão o comportamento esperado de cada feature
- testes futuros validarão que a implementação respeita esse comportamento

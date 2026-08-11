# Urbana Connect API

Aplicação backend Java/Spring Boot da Urbana Connect. Este diretório é a
fonte de código, recursos, testes, wrapper Gradle e imagens Docker da API.

## Requisitos

- JDK 21
- Docker apenas para os fluxos que usam MongoDB ou construção de imagem

## Comandos

Execute os comandos a partir deste diretório:

```bash
./gradlew projects
./gradlew test
./gradlew check
./gradlew bootRun
```

Para construir a imagem da aplicação:

```bash
docker build -f Dockerfile .
```

O `docker-compose.yml` local deste diretório fornece apenas os serviços de
desenvolvimento do MongoDB. A composição completa da POC pertence à camada de
infraestrutura local.

## Limites

A API expõe os contratos HTTP da Urbana Connect e integra-se ao runtime Hermes
por suas portas internas. O frontend, a configuração do Hermes e a operação da
POC são mantidos em diretórios próprios do monorepo; não copie credenciais ou
arquivos `.env` para este diretório.

## Ingressos e fronteiras

`/api/poc/conversations/{contactAlias}/messages` é o ingresso sintético
Hermes-first usado pela POC local e pelo `poc-chat`. O caminho persiste o
inbound, encaminha o texto ao Hermes Sessions API e devolve a projeção canônica
quando o turno termina.

`/api/webhook` permanece como ingresso legado para eventos WhatsApp. Ele não foi
migrado para Hermes nesta etapa e continua fora da evidência do round-trip local;
a migração WhatsApp → Hermes deve ser tratada em uma feature própria.

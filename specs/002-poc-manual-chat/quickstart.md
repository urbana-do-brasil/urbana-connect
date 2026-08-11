# Quickstart: Chat local da POC Hermes-first

Este documento descreve o fluxo esperado após a implementação da feature. O chat
é uma ferramenta local e não valida webhook, entrega ou leitura pela plataforma
do WhatsApp.

## Prerequisites

- Docker Desktop com Docker Engine e Compose acessíveis.
- `.env.poc` existente, com permissões restritas e fora do Git.
- Imagem/profile Hermes da POC já preparados.
- OpenRouter disponível para turnos reais do Hermes.
- Porta local `3000` livre ou `POC_CHAT_HOST_PORT` configurada com outra porta.

Node.js não será necessário para executar o stack pelo Compose. Node 24 LTS será
necessário apenas para desenvolver ou executar os testes do frontend diretamente
no host.

## 1. Build and start the complete local stack

Na raiz do repositório:

```bash
docker info
docker compose --env-file .env.poc -f infra/local-poc/docker-compose.poc.yml config --quiet
docker compose --env-file .env.poc -f infra/local-poc/docker-compose.poc.yml up -d --build
```

Verificar os serviços sem imprimir a configuração resolvida nem qualquer segredo:

```bash
docker compose --env-file .env.poc -f infra/local-poc/docker-compose.poc.yml ps
curl -fsS http://127.0.0.1:3000/health
curl -fsS http://127.0.0.1:8081/api/v1/health
curl -fsS http://127.0.0.1:8652/health
```

Abrir `http://127.0.0.1:3000` no navegador.

## 2. First manual conversation

1. Selecionar **Novo contato**.
2. Informar um nome amigável, por exemplo `Teste primeira compra`.
3. Enviar `Oi`.
4. Antes da resposta, enviar dois fragmentos adicionais dentro de quatro
   segundos, por exemplo `Quero reformar minha sala` e `Ainda não sei qual
   serviço escolher`.
5. Confirmar que os três balões enviados aparecem imediatamente.
6. Aguardar a janela real de agrupamento e o processamento Hermes.
7. Confirmar que a Urba responde no mesmo contato e se identifica como assistente
   virtual quando aplicável.

Não existe botão de `flush`; a espera é parte do comportamento sob teste.

## 3. Multiple-contact smoke test

1. Criar três contatos. Dois deles podem ter o mesmo nome amigável.
2. Enviar uma mensagem ao primeiro e trocar de conversa enquanto a Urba processa.
3. Enviar mensagens aos demais contatos.
4. Confirmar indicadores de não lido nas conversas não selecionadas.
5. Abrir cada contato e verificar que nenhuma mensagem ou resposta apareceu no
   histórico errado.
6. Recarregar a página e confirmar que os três contatos e seus históricos são
   restaurados.
7. Continuar uma das conversas para verificar memória de cliente recorrente.

O nome amigável da lista não deve fazer a Urba conhecer o nome da pessoa. A Urba
deve perguntar e aprender apenas informações efetivamente enviadas no chat.

## 4. Failure smoke test

Com uma mensagem já processada e o histórico preservado:

1. interromper somente o serviço Hermes por tempo suficiente para provocar uma
   falha controlada;
2. enviar uma mensagem de texto;
3. confirmar que a mensagem permanece visível e que a interface apresenta estado
   técnico sem inventar fala da Urba;
4. restabelecer Hermes;
5. usar **Tentar novamente**;
6. confirmar que a mensagem original e a resposta aparecem uma única vez.

Não remover volumes nem apagar MongoDB durante esse teste.

## 5. Frontend development checks

Dentro de `apps/poc-chat/`, com Node 24 LTS:

```bash
npm ci
npm run lint
npm run typecheck
npm test -- --run
npm run coverage
npm run build
```

Para testes de navegador determinísticos:

```bash
npx playwright install chromium
npm run test:e2e
```

O Playwright deve usar Chromium local por padrão. Execução em CI/container deve
fixar a mesma versão do pacote e da imagem de navegador.

## 6. Container and proxy checks

```bash
docker compose --env-file .env.poc -f infra/local-poc/docker-compose.poc.yml build poc-chat
docker compose --env-file .env.poc -f infra/local-poc/docker-compose.poc.yml up -d poc-chat
docker compose --env-file .env.poc -f infra/local-poc/docker-compose.poc.yml exec -T poc-chat nginx -t
curl -fsS http://127.0.0.1:3000/health
```

Validar também:

- uma chamada permitida do chat alcança a Urbana Connect sem header de
  autorização fornecido pelo JavaScript;
- `/api/poc/conversations/metrics` não é exposto pelo proxy do chat;
- `/flush` e `/payment-proof/approve` não são expostos pelo proxy do chat;
- o bundle e o armazenamento local não contêm os nomes das variáveis de segredo,
  valores de token ou transcripts completos;
- a porta publicada está vinculada somente a `127.0.0.1`.

## 7. Required regression

Na raiz do repositório, repetir os gates existentes da POC:

```bash
cd apps/urbana-connect-api
./gradlew check --offline --no-daemon --console=plain
cd ../..
python3 -m unittest discover -s integrations/hermes-agent/plugins/urbana-domain -p 'test*.py'
./integrations/hermes-agent/scripts/smoke-contract.sh
./integrations/hermes-agent/scripts/smoke-isolation.sh
./integrations/hermes-agent/scripts/verify-tool-surface.sh
```

Se o host tiver a porta do Ryuk ocupada, usar a alternativa já documentada pela
POC com `TESTCONTAINERS_RYUK_DISABLED=true`, sem alterar código da aplicação.

## 8. Acceptance evidence

A entrega somente poderá ser classificada como `verified` quando houver:

- build, typecheck, lint e cobertura do frontend aprovados;
- testes de componentes e Playwright aprovados;
- container saudável e `nginx -t` aprovado;
- smoke manual com três contatos, fragmentação, troca e reload;
- pelo menos um turno real Browser → Urbana Connect → Hermes → MongoDB → Browser;
- inspeção comprovando ausência de segredos e nomes amigáveis no tráfego para o
  Hermes;
- gates Java, Python e Hermes sem regressão.

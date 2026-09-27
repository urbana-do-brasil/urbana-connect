# PEE-126 — evidência sanitizada do primeiro provisionamento

Data da execução: 25 de setembro de 2026.
Escopo: local-only; cluster k3d dedicado `urbana-hml-platform-local`. Nenhum HML remoto ou produção foi acessado.

## Sequência executada

1. `scripts/hml-platform-local/tests/preflight.tests.ps1` — `STATIC_HML_PLATFORM_LOCAL_TESTS_OK`.
2. `scripts/hml-platform-local/tests/render.tests.ps1` — `STATIC_HML_PLATFORM_LOCAL_RENDER_OK`.
3. `cluster.ps1 -Create` — `HML_PLATFORM_LOCAL_CLUSTER_READY: urbana-hml-platform-local`.
4. `preflight.ps1` — `HML_PLATFORM_LOCAL_PREFLIGHT_OK`.
5. `start.ps1` — `HML_PLATFORM_LOCAL_PREFLIGHT_OK` e `HML_PLATFORM_LOCAL_STARTED`.
6. `smoke.ps1` — `HML_PLATFORM_LOCAL_SMOKE_OK`.
7. `status.ps1` — leitura abaixo.
8. `./gradlew test --no-daemon` — 522 testes, 0 falhas, 0 erros e 0 ignorados.

## Estado observado

- Namespace: `urbana-connect-hml-platform-local`.
- Contexto: `k3d-urbana-hml-platform-local`.
- Node: um node k3d Ready, `k3d-urbana-hml-platform-local-server-0`.
- MongoDB: `mongodb-0` `1/1 Running`.
- Init job: `mongodb-rs-init` `0/1 Completed`.
- API: deployment `urbana-connect` `1/1 Running`, zero restarts no readback.
- Service MongoDB: `ClusterIP`, `27017/TCP`.
- Service API: `ClusterIP`, `8081/TCP`; sem endereço público.
- PVC: `mongodb-data-mongodb-0` `Bound`, 5 GiB, `ReadWriteOnce`.
- Smoke: health/readiness passaram e POST sintético no `/api/webhook` foi aceito pelo handler inbound-only; os logs confirmaram `Webhook inbound-only aceito`.
- O caminho do smoke não chama Hermes, AI, WhatsApp ou SMTP; a política de rede do target limita o egress da API ao MongoDB.

## Proveniência das imagens

- API: referência `docker.io/library/urbana-connect-hml-platform-local-api:local`; ID `sha256:2d8fada6ea090857fb55284e3e17fe4b392f4b0020b47e2e1d0f1653f08abc6c`; revisão `6088a8c6a9184c51a18df4ccfd7bdb6df655254d`; worktree dirty `true` durante esta execução.
- MongoDB: referência fixada `docker.io/library/mongo:8.0@sha256:376f5173003b5408d7b8e6989667231c0bf0cefdce379d7c814910429d1a7a85`; ID observado igual ao digest fixado.
- O preflight leu 23 referências importadas no node e validou as referências exatas usadas pelos manifests `imagePullPolicy: Never`.

## Segredos e estado deixado

- O env sintético ficou fora do repositório, em diretório de dados local; seus valores não foram impressos.
- A verificação posterior dos cinco JSON sanitizados (`cluster-create`, `preflight`, `start`, `smoke`, `status`) não encontrou nenhum valor do env externo.
- A evidência contém apenas nomes de chaves, IDs/digests de imagem, target, namespace e estados operacionais.
- O stack ficou em execução para permitir inspeção posterior; parar com `scripts/hml-platform-local/stop.ps1` e, se necessário, excluir dados somente com `cluster.ps1 -Delete -ConfirmDataLoss`.

## Rollback, impacto e custo

O procedimento versionado está em `docs/hml-platform-local-pee-126.md`. O rollback é local e explícito; não há impacto em HML remoto, produção, DNS público ou provedores externos. O custo direto de cloud/provedor desta execução é zero; o custo residual é apenas o consumo temporário do Docker Desktop/k3d, MongoDB local, API local e armazenamento PVC de 5 GiB.

## Verificação da consolidação para PR #64 — 27 de setembro de 2026

- `scripts/hml-platform-local/tests/preflight.tests.ps1` — `STATIC_HML_PLATFORM_LOCAL_TESTS_OK`.
- `scripts/hml-platform-local/tests/render.tests.ps1` — `STATIC_HML_PLATFORM_LOCAL_RENDER_OK`.
- `preflight.ps1 -SkipRuntime` — `STATIC_HML_PLATFORM_LOCAL_PREFLIGHT_OK`.
- `render.ps1` — `HML_PLATFORM_LOCAL_RENDER_OK`.
- `kubectl kustomize infra/kubernetes/hml-platform-local` — sucesso, 461 linhas renderizadas.
- `./gradlew clean test --rerun-tasks --no-daemon` — `BUILD SUCCESSFUL`; 467 testes, 0 falhas, 0 erros e 0 ignorados no checkout do PR.
- `git diff --check` — sucesso.

A execução runtime descrita acima foi realizada em 25 de setembro de 2026 e revisada independentemente na rodada 3. A proveniência registrada para a imagem API é a revisão `6088a8c6a9184c51a18df4ccfd7bdb6df655254d`, com worktree dirty. Portanto, este histórico comprova que o ciclo local foi executado no worktree aceito, mas não afirma que o commit final publicado no PR #64 foi implantado ou reconstruído com o mesmo digest; o commit final foi validado pelas verificações estáticas e pela suíte Gradle acima.

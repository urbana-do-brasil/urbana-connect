# PEE-104 — Mongo replica set e fronteira transacional

## Objetivo e limite

Esta entrega promove o Mongo suportado pela POC e pelos manifestos de
homologação para um replica set reproduzível e deixa disponível o
`MongoTransactionManager` necessário à próxima implementação do fluxo de
retomada.

Ela não implementa HUMANO → URBA, worker de retomada, `reception_resumes`,
`reception_outbox` ou `reception_inbox`. A fronteira exercitada pelo teste usa
as coleções que já existem: atualização de `reception_conversations` e
inserção em `reception_messages` na mesma transação.

## Topologia suportada

- O nome do conjunto é `rs0`.
- A POC local usa `mongo:8.0`, hostname estável `mongodb` e um serviço
  `mongodb-rs-init` idempotente que inicia o membro `mongodb:27017` quando
  necessário.
- O Compose e o `dev-env.sh` mantidos em `apps/urbana-connect-api` usam a
  mesma inicialização e não oferecem mais um caminho standalone divergente.
- Homologação usa um `StatefulSet` de um membro. O pod é endereçado pelo DNS
  estável `urbana-connect-mongodb-0.urbana-connect-mongodb.<namespace>.svc.cluster.local`;
  um sidecar inicia o membro com esse endereço.
- O readiness do Mongo e o readiness da aplicação só liberam tráfego quando
  `hello` informa `setName=rs0` e `isWritablePrimary=true`. Um ping em um
  standalone não é suficiente.

## URI e retries

As URIs suportadas incluem:

```text
?replicaSet=rs0&retryWrites=true&retryReads=true&w=majority
```

`w=majority` mantém a confirmação compatível com o commit da topologia de um
membro. O customizer Java também força `retryWrites` e `retryReads` no cliente,
mas não remove a exigência de replica set feita pela URI e pelo readiness.
`retryWrites=false` não é fallback suportado.

Retries de uma operação de retomada futura devem repetir a transação inteira
com a mesma chave de idempotência e os mesmos predicados de versão. Não se
deve repetir apenas uma escrita isolada depois de um erro ambíguo.

## Índices e idempotência

As identidades atualmente materializadas são:

| Coleção | Identidade única | Finalidade |
|---|---|---|
| `reception_messages` | `eventId`; `providerMessageId` quando presente | impedir duplicação do evento recebido |
| `reception_turns` | `correlationId` | uma execução durável por turno |
| `reception_domain_tool_invocations` | `idempotencyKey` | não repetir ferramenta com a mesma intenção |
| `reception_active_turn_leases` | `hermesSessionId + status=RUNNING` | uma lease ativa por sessão |

Os índices futuros da retomada permanecem contrato para o próximo ticket:
`reception_resumes.resumeId`, `reception_outbox.eventId` e
`reception_inbox.(consumer,eventId)`, além da chave de idempotência do comando.
Eles não são criados nesta entrega porque os agregados correspondentes ainda
não existem.

Em runtime, a aplicação resolve as anotações de índice dos documentos
`@Document` e chama `ensureIndex` durante o startup. Assim, a unicidade
`providerMessageId` e `eventId` não depende de
`spring.data.mongodb.auto-index-creation`, que pode permanecer desabilitado
nos ambientes controlados. O teste de integração de provisionamento desabilita
explicitamente essa propriedade e inspeciona os índices reais da coleção.

## Um membro não é alta disponibilidade

Um replica set de um membro fornece a topologia Mongo necessária para
transações multi-documento e permite validar commit/rollback de forma
reproduzível. Ele não fornece redundância, failover, continuidade durante a
perda do nó, backup ou restore. A topologia de produção com múltiplos membros
e sua operação permanecem uma decisão separada.

## Evidência técnica

`MongoTransactionBoundaryIntegrationTest` usa Testcontainers e verifica que:

1. atualização da conversa e inserção da mensagem são persistidas juntas após
   commit;
2. uma falha após as duas operações reverte a atualização e a inserção, sem
   mensagem ou evento residual.

Nenhum manifesto foi aplicado em homologação ou produção por esta entrega.

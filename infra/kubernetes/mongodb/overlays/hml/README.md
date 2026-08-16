# Homolog MongoDB Runtime

Esta overlay define o provisionamento mínimo do MongoDB para homolog.

Inclui:
- `StatefulSet` com 1 réplica
- `Service` interno `urbana-connect-mongodb`
- `ConfigMap` com `mongod.conf`
- `PersistentVolumeClaim` via `volumeClaimTemplates`
- replica set `rs0` inicializado de forma idempotente por sidecar
- probes que só consideram o Mongo pronto quando `hello` informa `rs0` e um primary gravável

Não inclui:
- backup/restore
- alta disponibilidade: um único membro não fornece failover
- tuning avançado
- secret real versionado

## Aplicação

```bash
kubectl apply -k infra/kubernetes/mongodb/overlays/hml
```

## Pré-requisitos

- secret `urbana-connect-mongodb`
- secret `urbana-connect-mongodb-uri`
- `StorageClass` `local-path` disponível no cluster de homolog

## Observação

A URI da aplicação deve apontar para o service interno:

```text
mongodb://<username>:<password>@urbana-connect-mongodb.urbana-connect-hml.svc.cluster.local:27017/<database>?authSource=admin&replicaSet=rs0&retryWrites=true&retryReads=true&w=majority
```

A URI efetiva deve incluir `replicaSet=rs0`, `retryWrites=true`,
`retryReads=true` e `w=majority`. O template versionado em
`infra/kubernetes/secrets/templates/mongodb-uri-secret-template.yaml` já
contém essas opções.

O pod usa o DNS estável do StatefulSet
`urbana-connect-mongodb-0.urbana-connect-mongodb.<namespace>.svc.cluster.local`
como membro do replica set. A promoção para um replica set de um único membro
habilita transações multi-documento, mas não é uma topologia de alta
disponibilidade; o desenho de produção com múltiplos membros, failover,
backup e restore permanece fora da PEE-104.

O namespace `urbana-connect-hml` também é declarado na overlay da app. Isso é intencional para permitir `apply` isolado de cada componente sem depender da ordem de execução.

Em clusters locais como `kind`, pode ser necessário criar um alias temporário da `StorageClass` `local-path` caso ela não exista por padrão.

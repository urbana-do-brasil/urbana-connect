# Homolog MongoDB Runtime

Esta overlay define o provisionamento mínimo do MongoDB para homolog.

Inclui:
- `StatefulSet` com 1 réplica
- `Service` interno `urbana-connect-mongodb`
- `ConfigMap` com `mongod.conf`
- `PersistentVolumeClaim` via `volumeClaimTemplates`

Não inclui:
- backup/restore
- replicaset
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
mongodb://<username>:<password>@urbana-connect-mongodb.urbana-connect-hml.svc.cluster.local:27017/<database>?authSource=admin
```

O namespace `urbana-connect-hml` também é declarado na overlay da app. Isso é intencional para permitir `apply` isolado de cada componente sem depender da ordem de execução.

Em clusters locais como `kind`, pode ser necessário criar um alias temporário da `StorageClass` `local-path` caso ela não exista por padrão.

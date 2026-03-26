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
kubectl apply -k infra/k8s/mongodb/overlays/hml
```

## Pré-requisitos

- secret `urbana-connect-mongodb`
- secret `urbana-connect-mongodb-uri`

## Observação

A URI da aplicação deve apontar para o service interno:

```text
mongodb://<username>:<password>@urbana-connect-mongodb.urbana-connect-hml.svc.cluster.local:27017/<database>?authSource=admin
```

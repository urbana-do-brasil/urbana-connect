# Requirements checklist

- [x] O inbound é persistido antes do dispatch Hermes.
- [x] O request Hermes contém somente a mensagem conversacional atual.
- [x] A saída Hermes é persistida sem alteração de conteúdo em testes focados.
- [x] A projeção e o frontend exibem a mesma saída persistida em testes focados e
      E2E determinístico.
- [x] Prefixo, fallback, wrapper de estado e non-prospect local foram removidos
      do caminho normal.
- [x] Leases, idempotência, ordenação, sessão e reconciliação permanecem verdes.
- [x] Falha técnica não cria bubble conversacional.
- [x] Testes Java, frontend, profile, corpus e E2E determinístico foram
      executados.
- [x] Evidência literal Hermes → Mongo → HTTP → UI em uma nova resposta live do
      modelo; as quatro strings tiveram 83 caracteres e o mesmo SHA-256.

## Aceite final

**Resultado**: `verified`

Os testes automatizados, a QA independente e a validação live do modelo
passaram. A duplicata `PocReceptionWorker 2.java` foi preservada e isolada
apenas no processo de compilação temporário; ela é um risco preexistente do
worktree, não uma falha do pass-through.

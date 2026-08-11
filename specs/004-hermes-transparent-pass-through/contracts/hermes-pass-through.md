# Contract: Hermes pass-through

## Request

O conteúdo conversacional enviado para o Hermes deve ser a mensagem atual do
usuário. Não deve conter prefixo de estado, fatos confirmados, nome visual,
política comercial ou instrução de controle gerada pelo backend.

## Response

O backend extrai somente o conteúdo textual retornado pelo contrato Hermes já
configurado e o persiste como outbound. Se o transporte falhar, não existe
resposta conversacional para persistir.

```text
Hermes text -> outbound Mongo -> conversation projection -> browser
```

Cada seta deve preservar a string exatamente.

## Technical failure

Falhas de transporte, timeout ambíguo e reconciliação permanecem estados
técnicos. Nenhuma delas deve gerar uma mensagem artificial da Urba.

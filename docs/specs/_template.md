# Template de Spec SDD

Use este arquivo como ponto de partida antes de implementar qualquer feature da Urba.

Objetivo do template:

- explicitar o comportamento esperado antes do código
- reduzir ambiguidade para humanos e agentes de IA
- alinhar implementação, testes e review contra o mesmo contrato

## Metadados

- `Título da feature`:
- `Ticket Jira`:
- `Status`: rascunho | em revisão | aprovado | implementado
- `Responsável pela spec`:
- `Data`:

## 1. Contexto

Descreva o problema e por que a feature existe.

Perguntas que esta seção deve responder:

- qual necessidade do sistema ou do negócio está sendo atendida?
- em que fluxo a feature se encaixa?
- o que já existe hoje e o que está faltando?

## 2. Comportamentos esperados

Liste o comportamento observável que a implementação precisa entregar.

Boas práticas:

- escreva em termos de entrada e saída
- prefira comportamento verificável a intenção vaga
- enumere cenários distintos quando houver múltiplos caminhos

Exemplo de formato:

1. Dado `X`, quando `Y`, então o sistema deve `Z`.
2. Dado `A`, quando `B`, então o sistema deve `C`.

## 3. Critérios de aceite

Liste as condições mínimas para considerar a feature pronta.

Regras:

- devem ser verificáveis
- devem permitir decidir claramente entre `pronto` e `não pronto`
- devem cobrir comportamento, segurança e operação quando aplicável

## 4. Edge cases

Liste situações de borda, entradas inválidas, falhas operacionais e restrições.

Exemplos:

- payload ausente ou inválido
- autenticação incorreta
- recurso externo indisponível
- dados obrigatórios faltando

## 5. Observabilidade e validação

Descreva como a feature será validada e observada.

Cobrir, quando fizer sentido:

- testes automatizados esperados
- logs esperados
- métricas relevantes
- smoke test/manual test

## 6. Fora de escopo

Deixe explícito o que esta feature não cobre para evitar expansão silenciosa de escopo.

## 7. Dúvidas em aberto

Liste decisões pendentes ou pontos que precisam de confirmação antes da implementação.

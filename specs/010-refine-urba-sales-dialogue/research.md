# Research — Refinamento da conversa comercial da Urba

## Decisão 1 — Separar personalidade de guardrails determinísticos

**Decisão:** manter a naturalidade, vocabulário e energia no `SOUL.md`, mas
preservar aceite, pagamento, comprovante, handoff e catálogo nas regras já
existentes do backend.

**Racional:** o transcript Yohanna mostrou que uma instrução de personalidade
não consegue corrigir sozinha a rejeição de “aceito”, a ordem do pagamento ou a
mensagem gerada por `prepare_payment`. A constituição também exige que regras
de negócio não nasçam em uma integração externa.

**Alternativas consideradas:** restringir a mudança ao SOUL (insuficiente para
“Aceito”); reescrever o fluxo Hermes (fora do escopo e desnecessário).

## Decisão 2 — Catálogo como fonte dos fatos do serviço

**Decisão:** enriquecer a apresentação canônica já retornada por
`list_available_services`, em especial Decor Reforma, sem inserir preços ou
links reais no profile.

**Racional:** `ServiceCatalogItem` já centraliza preço, área, entregas,
responsabilidades, exclusões e suporte. O SOUL orienta a forma de falar; o
catálogo fornece os fatos que podem ser ditos.

**Alternativas consideradas:** copiar a descrição completa no SOUL (duplica e
desatualiza fonte comercial); alterar o roteiro legado (não é fonte operacional).

## Decisão 3 — Aceite simples contextual

**Decisão:** reconhecer `Aceito` isolado somente com termos apresentados para o
serviço/ambiente atual; aceitar também uma mensagem inequívoca com método de
pagamento e o par consecutivo `Aceito` + método.

**Racional:** remove o retrabalho observado sem permitir aceite antecipado,
negação ou “ok”. A validação continua determinística e auditável pelo transcript,
recurso de termos, estado e invocação.

**Alternativas consideradas:** aceitar qualquer mensagem contendo “aceito”
(aceitaria “aceito depois”); exigir a frase longa atual (reproduz o defeito da
Yohanna).

## Decisão 4 — Quantidade como copy, player como TODO

**Decisão:** incluir na mensagem de pagamento a orientação “1 serviço por
ambiente” e pedido de comprovante. Registrar que os links da POC são fixtures e
que a seleção de quantidade no player real será verificada antes da homologação.

**Racional:** não existe player/link real disponível. A feature pode corrigir a
expectativa textual agora sem inventar capacidade visual nem criar integração.

**Alternativas consideradas:** implementar seletor ou fallback operacional agora
(sem provedor escolhido e fora do escopo aprovado).

## Decisão 5 — Validar comportamento sem alterar a superfície Hermes

**Decisão:** preservar as seis ferramentas e os contratos do plugin; atualizar
somente suas descrições quando necessário para refletir a copy.

**Racional:** os scripts de superfície e isolamento já protegem o limite de
integração; nenhuma nova ferramenta é necessária.

**Alternativas consideradas:** adicionar ferramenta de quantidade (prematuro e
sem player); expor estado interno ao modelo (viola segurança do profile).

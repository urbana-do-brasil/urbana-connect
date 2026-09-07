# Requirements quality checklist — PEE-23 closeout

**Purpose**: validar a qualidade da especificação, não a implementação.
**Created**: 2026-09-02
**Audience**: autor, PO, jurídico e revisão técnica antes do desenvolvimento.

## Completude e rastreabilidade

- [ ] CHK001 A spec identifica explicitamente o limite da PEE-23 e os fluxos pós-pagamento excluídos? [Completeness, Spec §1 e §7]
- [ ] CHK002 Cada requisito funcional está associado a um critério de aceite observável? [Traceability, Spec §3 e §5]
- [ ] CHK003 Os quatro serviços, ambientes e regra de um serviço por checkout estão definidos sem depender de conhecimento externo? [Completeness, Spec §2.1]
- [ ] CHK004 As dependências de documentos jurídicos, provider, HML, SMTP, URL e retenção estão nomeadas com responsável/insumo esperado? [Dependency, Spec §6]

## Clareza e consistência

- [ ] CHK005 “Aceite válido” está definido de forma inequívoca e consistente entre WhatsApp, tela, serviço e versão? [Clarity, Spec §2.2]
- [ ] CHK006 A diferença entre recusa explícita, abandono, expiração e decisão duplicada está descrita sem sobreposição? [Consistency, Spec §2.2 e FR-006]
- [ ] CHK007 A regra de conteúdo jurídico igual em HML/PROD e checkout diferente por ambiente está compatível com a regra de disponibilidade do catálogo? [Consistency, Spec §2.1 e §2.2]
- [ ] CHK008 A spec distingue requisito desejável de PIX/cartão e decisão obrigatória quando houver limitação do provider? [Clarity, Spec §2.3]
- [ ] CHK009 A expressão “retoma automaticamente o WhatsApp” está vinculada a um resultado observável e a um tratamento de falha? [Measurability, Spec FR-007]

## Cenários e exceções

- [ ] CHK010 Os cenários primário, alternativo, recusa, abandono, expiração, troca de serviço e troca de versão estão cobertos? [Coverage, Spec §2.2 e §5]
- [ ] CHK011 Os cenários de falha pré-dispatch, pós-dispatch ambígua, entrega, persistência e saída inválida têm ações distintas? [Coverage, Spec §2.4 e §4]
- [ ] CHK012 A spec define o que acontece quando o conteúdo já cabe na tela e quando o cliente usa teclado, touch ou leitor de tela? [Edge Case, Spec §4]
- [ ] CHK013 Está explícito o comportamento quando o WhatsApp não pode receber a continuação automática? [Recovery, Spec FR-007 e FR-008]
- [ ] CHK014 A impossibilidade de resolver `destinationRef` é tratada como falha segura, sem presumir um telefone a partir de hash? [Security, Spec §3 e §4]

## Segurança, privacidade e auditoria

- [ ] CHK015 Os campos auditáveis e os campos proibidos em logs estão delimitados, incluindo a separação entre TTL da sessão e retenção jurídica? [Completeness, Spec §4 e data-model]
- [ ] CHK016 O requisito deixa claro que scroll é evidência de interação, não prova de leitura? [Clarity, Spec §2.2 e FR-005]
- [ ] CHK017 As exigências de token, binding, replay, CORS, CSP, cache, referrer e conteúdo escapado são verificáveis sem depender de uma tecnologia específica? [Security, Spec §4]
- [ ] CHK018 O prazo de retenção parametrizado está identificado como decisão jurídica pendente, sem um valor inventado? [Assumption, Spec §2.2 e §6]

## Aceite e dependências

- [ ] CHK019 Os critérios de conclusão exigem evidência atual em HML e não aceitam somente testes locais ou evidência histórica de PEE-88? [Acceptance Criteria, Spec §5]
- [ ] CHK020 A ordem PEE-93 → provider → validação HML → PEE-92 → PEE-23 está documentada e não permite encerrar com dependência pendente? [Dependency, Spec §5]
- [ ] CHK021 A spec não pressupõe escolha de provedor, credenciais, conteúdo jurídico ou ativação de produção antes da aprovação correspondente? [Boundary, Spec §6 e §7]
- [ ] CHK022 Os termos “produção”, “sandbox”, “checkout”, “handoff” e “provider ID” estão suficientemente definidos para PO, jurídico e implementação? [Clarity, Spec §2–§4]

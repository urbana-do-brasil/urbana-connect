# POC Manual Chat

Aplicação React/Vite para testar manualmente o fluxo conversacional local da
Urbana Connect. O chat usa somente a API HTTP pública da aplicação Urbana;
não acessa MongoDB ou Hermes diretamente.

## Desenvolvimento

Requer Node.js 24 LTS.

```bash
cd apps/poc-chat
npm ci
npm run test -- --run
npm run typecheck
npm run lint
npm run build
```

Para executar a aplicação localmente:

```bash
npm run dev
```

Os testes rápidos ficam em `src/` e os cenários Playwright em `e2e/`:

```bash
npm run test:e2e
```

## Container

O contrato estático do container pode ser verificado com:

```bash
./container.test.sh
```

As verificações live dependem do stack local e de um `.env.poc` configurado na
raiz do repositório. Tokens permanecem no ambiente do runtime e não devem ser
expostos no navegador, em logs ou neste README.

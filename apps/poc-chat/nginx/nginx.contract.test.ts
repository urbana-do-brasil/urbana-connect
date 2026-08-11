import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

const nginxDirectory = dirname(fileURLToPath(import.meta.url));
const appRoot = resolve(nginxDirectory, "..");
const repositoryRoot = resolve(appRoot, "../..");
const readApp = (relativePath: string) =>
  readFileSync(resolve(appRoot, relativePath), "utf8");
const readRepository = (relativePath: string) =>
  readFileSync(resolve(repositoryRoot, relativePath), "utf8");

describe("poc-chat container contract", () => {
  it("keeps the browser surface to the two chat routes and health", () => {
    const template = readApp("nginx/default.conf.template");

    expect(template).toMatch(
      /location\s+~\*?\s+"?\^\/api\/poc\/conversations\/\(manual-/,
    );
    expect(template).toMatch(/\/messages\$\s*"?\s*\{/);
    expect(template).toMatch(/location\s+~\*?\s+"?\^\/api\/poc\/conversations\/\(manual-[^\n]+\)\$"?\s*\{/);
    expect(template).toMatch(/location\s*=\s*\/health\s*\{/);
    expect(template).toMatch(/location\s+\/api\/\s*\{\s*return\s+404;/s);
    expect(template).toMatch(/try_files\s+\$uri\s*=404;/);
  });

  it("replaces browser authorization with the server-side POC token", () => {
    const template = readApp("nginx/default.conf.template");

    expect(template).toContain(
      'proxy_set_header Authorization "Bearer ${HERMES_POC_API_TOKEN}";',
    );
    expect(template).not.toMatch(
      /proxy_set_header\s+Authorization\s+\$http_authorization/,
    );
    expect(template).toMatch(/proxy_set_header\s+Proxy-Authorization\s+""/);
    expect(template).not.toMatch(/add_header\s+Access-Control-Allow-Origin/);
    expect(template).not.toMatch(/proxy_pass\s+http:\/\/hermes/);
  });

  it("has a Node 24 build stage and an unprivileged Nginx runtime", () => {
    const dockerfile = readApp("Dockerfile");
    const runtimeStage = dockerfile.slice(dockerfile.indexOf("\nFROM ", 1));

    expect(dockerfile).toMatch(/^FROM node:24-alpine/m);
    expect(dockerfile).toMatch(/^RUN npm ci/m);
    expect(dockerfile).toMatch(/^RUN npm run build/m);
    expect(runtimeStage).toMatch(/^FROM nginxinc\/nginx-unprivileged:/m);
    expect(runtimeStage).not.toMatch(/node(?:js)?\s+(?:server|dist|run)/i);
    expect(runtimeStage).toMatch(/USER 101(?::101)?/);
  });

  it("generates the token-bearing configuration in a runtime temporary path", () => {
    const entrypoint = readApp("docker-entrypoint.d/10-poc-token.sh");
    const dockerfile = readApp("Dockerfile");
    const compose = readRepository("infra/local-poc/docker-compose.poc.yml");

    expect(entrypoint).toMatch(/HERMES_POC_API_TOKEN/);
    expect(entrypoint).toMatch(/envsubst/);
    expect(dockerfile).toContain("/docker-entrypoint.d/10-poc-token.sh");
    expect(compose).toMatch(/\/etc\/nginx\/conf\.d/);
    expect(compose).toMatch(/tmpfs:/);
  });
});

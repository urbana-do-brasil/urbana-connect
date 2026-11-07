# AGENTS.md

Urbana Connect — agente de atendimento via WhatsApp com IA para a Urbana do Brasil.
Stack: Java 21 LTS, Spring Boot 3.4.x, Gradle 8.x, MongoDB, JUnit 5 + Testcontainers.
Leia `README.md` e `docs/engineering-principles.md` antes de implementar.

## Agent skills

### Issue tracker

Issues live in GitHub Issues (`urbana-do-brasil/urbana-connect`) via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-role vocabulary: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `GLOSSARY.md` at the repo root and ADRs in `docs/adr/`. See `docs/agents/domain.md`.

# Production application overlay

Esta overlay descreve a topologia de produção sem ativar o fluxo comercial,
sem inserir segredos e sem autorizar deploy nesta história. O ConfigMap deixa
Hermes, consentimento web e catálogo operacional em modo fail-closed até que a
subtask de provider, os artefatos jurídicos e a validação de HML sejam
concluídos e aprovados separadamente.

Antes de qualquer ativação operacional, criar os secrets no namespace
`urbana-connect`, apontar o DNS `api.urbanadobrasil.com` e validar o ingress e o
certificado TLS. O segredo `urbana-connect-terms` é opcional no manifesto para
permitir o bootstrap seguro; torna-se obrigatório quando
`TERMS_CONSENT_ENABLED=true`.

`CATALOG_FIXTURE_FALLBACK=false` permanece explícito para impedir que links de
teste sejam oferecidos durante o bootstrap de produção.

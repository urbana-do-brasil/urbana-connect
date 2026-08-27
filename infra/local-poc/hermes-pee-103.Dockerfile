# Local PEE-103 image used by the POC Compose stack.
#
# The published v0.20.0 image is the exact parent runtime pinned by the
# integration. Only the files changed by PEE-103 are overlaid from the sibling
# Hermes checkout, preserving an image-based runtime without source bind mounts.
FROM urbana-hermes-agent:0.20.0

ARG HERMES_GIT_SHA=2f5472a15a026b6bd5847ad65058f1565d2b40ba

LABEL org.opencontainers.image.version="v2026.8.3-pee-103" \
      org.opencontainers.image.revision="${HERMES_GIT_SHA}" \
      org.opencontainers.image.source="https://github.com/NousResearch/hermes-agent"

COPY hermes-agent/gateway/platforms/api_server.py /opt/hermes/gateway/platforms/api_server.py
COPY hermes-agent/gateway/resume_capability.py /opt/hermes/gateway/resume_capability.py
COPY hermes-agent/hermes_state_common.py /opt/hermes/hermes_state_common.py

RUN printf '%s\n' "${HERMES_GIT_SHA}" > /opt/hermes/.hermes_build_sha

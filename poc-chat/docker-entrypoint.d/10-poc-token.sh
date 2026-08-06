#!/bin/sh
set -eu

token="${HERMES_POC_API_TOKEN:-}"
if [ -z "$token" ]; then
    echo "HERMES_POC_API_TOKEN must be set for the local POC proxy" >&2
    exit 1
fi

# The token is embedded in a quoted Nginx header. Reject characters that could
# terminate that value or introduce another directive during envsubst.
case "$token" in
    *[!A-Za-z0-9._~+\/=-]*)
        echo "HERMES_POC_API_TOKEN contains unsupported characters" >&2
        exit 1
        ;;
esac

template=/etc/nginx/poc-default.conf.template
output=/etc/nginx/conf.d/default.conf

if [ ! -r "$template" ]; then
    echo "Nginx POC template is missing" >&2
    exit 1
fi

if ! command -v envsubst >/dev/null 2>&1; then
    echo "envsubst is required to render the Nginx POC template" >&2
    exit 1
fi

umask 077
envsubst '${HERMES_POC_API_TOKEN}' < "$template" > "$output"
chmod 0444 "$output"

if grep -Fq '${HERMES_POC_API_TOKEN}' "$output"; then
    echo "Nginx POC template was not fully rendered" >&2
    exit 1
fi

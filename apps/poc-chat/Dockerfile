FROM node:24-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.27-alpine@sha256:65e3e85dbaed8ba248841d9d58a899b6197106c23cb0ff1a132b7bfe0547e4c0

USER root

COPY --from=build /app/dist/ /usr/share/nginx/html/
COPY nginx/nginx.conf /etc/nginx/nginx.conf
COPY nginx/default.conf.template /etc/nginx/poc-default.conf.template
COPY docker-entrypoint.d/10-poc-token.sh /docker-entrypoint.d/10-poc-token.sh

# The stock envsubst template is deliberately removed. The POC template is
# rendered by the dedicated script with an explicit variable allowlist so
# Nginx variables such as $http_authorization are never expanded by envsubst.
RUN rm -f /etc/nginx/templates/default.conf.template /etc/nginx/conf.d/default.conf \
    && chmod 0555 /docker-entrypoint.d/10-poc-token.sh

USER 101:101

EXPOSE 8080

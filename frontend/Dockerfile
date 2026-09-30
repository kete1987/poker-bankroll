# syntax=docker/dockerfile:1

# ---- Build: static files ----
FROM node:24-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# ---- Runtime: nginx serving the SPA and proxying /api, non-root, port 8080 ----
FROM nginxinc/nginx-unprivileged:stable-alpine

# Backend address used by the /api proxy (host:port). Rendered into the config at startup.
ENV API_UPSTREAM=api:8080

COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 8080

# 127.0.0.1, not localhost: in Alpine localhost resolves to ::1 and nginx listens on IPv4 only
# (listening on [::] would fail on hosts with IPv6 disabled).
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1

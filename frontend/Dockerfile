# syntax=docker/dockerfile:1

# ---- Build: the Angular app ----
FROM node:25-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
RUN npm run build

# ---- Serve: the app, with /api and Keycloak behind the same HTTPS address ----
FROM nginx:1.30-alpine
RUN apk add --no-cache openssl
ENV FREEZER_URL=https://localhost:8443
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY nginx/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY --chmod=755 nginx/15-certificate.sh /docker-entrypoint.d/15-certificate.sh
COPY --from=build /app/dist/freezer-frontend/browser /usr/share/nginx/html

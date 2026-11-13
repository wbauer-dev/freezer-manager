# syntax=docker/dockerfile:1

# ---- Build: the Angular app ----
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
RUN npm run build

# ---- Serve: static files, /api proxied to the backend ----
FROM nginx:1.30-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/freezer-frontend/browser /usr/share/nginx/html

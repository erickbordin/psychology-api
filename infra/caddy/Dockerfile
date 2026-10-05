FROM node:20-alpine AS front
ARG VITE_API_URL=/api
ENV VITE_API_URL=$VITE_API_URL
WORKDIR /front
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/index.html frontend/tsconfig.json frontend/tsconfig.node.json frontend/vite.config.ts frontend/playwright.config.ts ./
COPY frontend/public ./public
COPY frontend/e2e ./e2e
COPY frontend/src ./src
RUN npm run build

FROM caddy:2-alpine
COPY --from=front /front/dist /srv/front
COPY infra/caddy/Caddyfile /etc/caddy/Caddyfile

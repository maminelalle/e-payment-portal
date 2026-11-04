# Image de production : build Angular, puis Nginx qui sert l'application et relaie /api, /auth et /simulateur.
FROM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npx ng build --configuration production

FROM nginx:1.31.8-alpine
COPY --from=build /src/dist/frontend/browser /usr/share/nginx/html
# Modèle traité au démarrage par l'image officielle (envsubst) : PORTAL_HOST, etc.
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY nginx-security-headers.conf /etc/nginx/snippets/security-headers.conf

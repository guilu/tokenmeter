FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
# Public Google Analytics 4 Measurement ID, inlined by Vite at build time (TKM-64).
ARG VITE_GA_MEASUREMENT_ID=""
# Deployed commit, for the preproduction ribbon. An argument because there is no
# way to work it out inside the image: `.git` is in the .dockerignore and
# node:alpine ships no git. Empty = the ribbon warns without a version.
ARG VITE_BUILD_SHA=""
ENV VITE_BUILD_SHA=$VITE_BUILD_SHA
ENV VITE_GA_MEASUREMENT_ID=$VITE_GA_MEASUREMENT_ID
RUN npm run build

FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80

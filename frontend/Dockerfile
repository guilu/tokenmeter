FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
# Public Google Analytics 4 Measurement ID, inlined by Vite at build time (TKM-64).
ARG VITE_GA_MEASUREMENT_ID=""
# Deployed commit, for the preproduction ribbon.
#
# An argument because there is no way to work it out inside the image: the build
# context is `./frontend` and `.git` lives at the repository root, so it is not
# even in the context. Whoever builds passes it:
#
#   VITE_BUILD_SHA=$(git rev-parse --short HEAD) docker compose up --build -d
#
# Without it the build falls back to its own date, below.
ARG VITE_BUILD_SHA=""
ENV VITE_BUILD_SHA=$VITE_BUILD_SHA
ENV VITE_GA_MEASUREMENT_ID=$VITE_GA_MEASUREMENT_ID
# With no sha the ribbon lost its second line, which is what happened on every
# deploy because `docker compose up --build` passes no argument. The build date
# is not the commit, but it is a true fact the image knows about itself and it
# answers what one actually asks looking at a preproduction: how fresh is this.
RUN if [ -z "$VITE_BUILD_SHA" ]; then \
      export VITE_BUILD_SHA="$(date -u +%m-%d\ %H:%M)"; \
    fi; \
    npm run build

FROM nginx:1.29-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80

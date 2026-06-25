FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon
COPY src ./src
COPY config ./config
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 1001 app \
    && useradd --system --uid 1001 --gid app --home /app --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/build/libs/*.jar app.jar
# DJL extracts its native tokenizer lib (libtokenizers.so) to its cache dir and mmaps it.
# The default falls back to /tmp, which is often mounted noexec in hardened runtimes →
# "UnsatisfiedLinkError: failed to map segment from shared object". Pin the cache to an
# exec-allowed, app-owned dir on the image filesystem instead.
RUN mkdir -p /app/.djl && chown app:app /app/.djl
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=docker
ENV DJL_CACHE_DIR=/app/.djl
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

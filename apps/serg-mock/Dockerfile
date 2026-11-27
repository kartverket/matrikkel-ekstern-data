# JDK image for tilgang til gradle
FROM dhi.io/eclipse-temurin:25.0.5.8.1.2-jdk-alpine-dev@sha256:90c163427da0f635526f6b00dfd317b475a9f38e6e2ce569b20e79aa6090da8c AS builder

WORKDIR /workspace
COPY . .
ARG GITHUB_USER
ARG KV_PACKAGES_PAT
RUN ./gradlew :apps:serg-mock:installDist --no-daemon

# Bruker dev image for å ha tilgang til shell
FROM dhi.io/eclipse-temurin:25.0.5.8.1.2-alpine-dev@sha256:177424084716d17ad522b81e8ba2aaf8a78c7ea6f09f644e089ad27f48e07f5c AS runtime

WORKDIR /app
COPY --from=builder /workspace/apps/serg-mock/build/install/serg-mock/lib /app/lib
EXPOSE 8084
ENTRYPOINT ["java", "-cp", "/app/lib/*", "no.kartverket.serg.mock.AppKt"]

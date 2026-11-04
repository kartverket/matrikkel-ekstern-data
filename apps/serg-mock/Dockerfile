# JDK image for tilgang til gradle
FROM dhi.io/eclipse-temurin:25.0.5.8-jdk-alpine3.24-dev@sha256:98d05ec86c91fdcd0e1d5922d2eb34099e5f5dd822028a9d8777ca0d2daf2503 AS builder

WORKDIR /workspace
COPY . .
ARG GITHUB_USER
ARG KV_PACKAGES_PAT
RUN ./gradlew :apps:serg-mock:installDist --no-daemon

# Bruker dev image for å ha tilgang til shell
FROM dhi.io/eclipse-temurin:25.0.5.8-alpine3.24-dev@sha256:632ffcc51872097d8a7c5077d13a2ed36f248db1b0cf2a878d7146fa695bbb3e AS runtime

WORKDIR /app
COPY --from=builder /workspace/apps/serg-mock/build/install/serg-mock/lib /app/lib
EXPOSE 8084
ENTRYPOINT ["java", "-cp", "/app/lib/*", "no.kartverket.serg.mock.AppKt"]

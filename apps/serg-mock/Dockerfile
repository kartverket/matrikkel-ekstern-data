# JDK image for tilgang til gradle
FROM dhi.io/eclipse-temurin:27.0.1.1-jdk-alpine3.24-dev@sha256:7994120a00b2b1a12693a95715e0d7fd622a7ad182d4dd384d436e376b59c955 AS builder

WORKDIR /workspace
COPY . .
ARG GITHUB_USER
ARG KV_PACKAGES_PAT
RUN ./gradlew :apps:serg-mock:installDist --no-daemon

# Bruker dev image for å ha tilgang til shell
FROM dhi.io/eclipse-temurin:27.0.1.1-alpine3.24-dev@sha256:cdf9338e86d43658b940cd688fb50589599655048dd02b5635fd827aceeee302 AS runtime

WORKDIR /app
COPY --from=builder /workspace/apps/serg-mock/build/install/serg-mock/lib /app/lib
EXPOSE 8084
ENTRYPOINT ["java", "-cp", "/app/lib/*", "no.kartverket.serg.mock.AppKt"]

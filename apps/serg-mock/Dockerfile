# JDK image for tilgang til gradle
FROM dhi.io/eclipse-temurin:25.0.5.8-jdk-alpine3.24-dev@sha256:e8da9bf2eeeaf182609d207a92d837771d3f68776e8b0149267c0bb0a5883453 AS builder

WORKDIR /workspace
COPY . .
ARG GITHUB_USER
ARG KV_PACKAGES_PAT
RUN ./gradlew :apps:serg-mock:installDist --no-daemon

# Bruker dev image for å ha tilgang til shell
FROM dhi.io/eclipse-temurin:25.0.5.8-alpine3.24-dev@sha256:5fc671b6380de25a0627c6ba9405a8939754991481134c73c42e4a972c7ffc0c AS runtime

WORKDIR /app
COPY --from=builder /workspace/apps/serg-mock/build/install/serg-mock/lib /app/lib
EXPOSE 8084
ENTRYPOINT ["java", "-cp", "/app/lib/*", "no.kartverket.serg.mock.AppKt"]

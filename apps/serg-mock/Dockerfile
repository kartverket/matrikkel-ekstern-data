# JDK image for tilgang til gradle
FROM dhi.io/eclipse-temurin:25.0.5.8-jdk-alpine-dev@sha256:8ba37e081557720efc6f601bee83cf7660c995ed110e33cf3e416b0e3a8b1cdc AS builder

WORKDIR /workspace
COPY . .
ARG GITHUB_USER
ARG KV_PACKAGES_PAT
RUN ./gradlew :apps:serg-mock:installDist --no-daemon

# Bruker dev image for å ha tilgang til shell
FROM dhi.io/eclipse-temurin:25.0.5.8-alpine-dev@sha256:99b5ac0ab085f0ce57a80757f2696eb08348df2bacab312e1d7fe340bfd7e0df AS runtime

WORKDIR /app
COPY --from=builder /workspace/apps/serg-mock/build/install/serg-mock/lib /app/lib
EXPOSE 8084
ENTRYPOINT ["java", "-cp", "/app/lib/*", "no.kartverket.serg.mock.AppKt"]

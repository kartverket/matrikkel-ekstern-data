# JDK image for tilgang til gradle
FROM dhi.io/eclipse-temurin:25.0.5.8-jdk-alpine-dev@sha256:6655d4130a0e9415a4ecbf041d03a5a78b443bdbb7b21e216867d9f5414b4908 AS builder

WORKDIR /workspace
COPY . .
ARG GITHUB_USER
ARG KV_PACKAGES_PAT
RUN ./gradlew :apps:serg-mock:installDist --no-daemon

# Bruker dev image for å ha tilgang til shell
FROM dhi.io/eclipse-temurin:25.0.5.8-alpine-dev@sha256:cb9ef534f22958ac87a548f0e5c5afa84c4e8046dab84d6983f61de67ff6c5d1 AS runtime

WORKDIR /app
COPY --from=builder /workspace/apps/serg-mock/build/install/serg-mock/lib /app/lib
EXPOSE 8084
ENTRYPOINT ["java", "-cp", "/app/lib/*", "no.kartverket.serg.mock.AppKt"]

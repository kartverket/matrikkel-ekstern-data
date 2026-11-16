# JDK image for tilgang til gradle
FROM dhi.io/eclipse-temurin:25.0.5.8-jdk-alpine3.24-dev@sha256:4f7d8077f3438cf46e6234b19ab637c1b547a724a8bdee76c7a4db1e04a3bfc9 AS builder

WORKDIR /workspace
COPY . .
ARG GITHUB_USER
ARG KV_PACKAGES_PAT
RUN ./gradlew :apps:serg-mock:installDist --no-daemon

# Bruker dev image for å ha tilgang til shell
FROM dhi.io/eclipse-temurin:25.0.5.8-alpine3.24-dev@sha256:145361df3636e743c70140b812acc4b0aec53625b4881bab5d6b958451465667 AS runtime

WORKDIR /app
COPY --from=builder /workspace/apps/serg-mock/build/install/serg-mock/lib /app/lib
EXPOSE 8084
ENTRYPOINT ["java", "-cp", "/app/lib/*", "no.kartverket.serg.mock.AppKt"]

FROM ghcr.io/kartverket/emudhra-images/jdk/dockerhub:jdk-25.0-alpine3.24@sha256:9040fd4372ac7c4581712c77ba9324a175ff9f9679ab3ff60ea5c12eb02fcdb9
WORKDIR /workspace
COPY . .
ARG GITHUB_USER
ARG KV_PACKAGES_PAT
RUN ./gradlew :apps:serg-mock:installDist --no-daemon

FROM ghcr.io/kartverket/emudhra-images/jre/dockerhub:jre-25.0-alpine3.24@sha256:a1a2a58677616ad8d737329c6c00e93d9a3c45661a0effc2bf7a93924c0b76fb
WORKDIR /app
COPY --from=build /workspace/apps/serg-mock/build/install/serg-mock /app
EXPOSE 8084
ENTRYPOINT ["/app/bin/serg-mock"]

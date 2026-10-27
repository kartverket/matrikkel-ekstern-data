FROM ghcr.io/kartverket/emudhra-images/jdk/dockerhub:jdk-25.0-alpine3.24@sha256:3b9c9aa8f9cd11b2660bace83082b223abc6923a9fc54bb7e08c9f122565db72
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

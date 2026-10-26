FROM ghcr.io/kartverket/emudhra-images/jdk/dockerhub:jdk-25.0-alpine3.24@sha256:9040fd4372ac7c4581712c77ba9324a175ff9f9679ab3ff60ea5c12eb02fcdb9
WORKDIR /workspace
COPY . .
ARG GITHUB_USER
ARG KV_PACKAGES_PAT
RUN ./gradlew :apps:serg-mock:installDist --no-daemon

FROM ghcr.io/kartverket/emudhra-images/jre/dockerhub:jre-25.0-alpine3.24@sha256:2d2e8939adc7327871075062d4cca2dbd8da7542d903861dc9d0b84c96b3a722
WORKDIR /app
COPY --from=build /workspace/apps/serg-mock/build/install/serg-mock /app
EXPOSE 8084
ENTRYPOINT ["/app/bin/serg-mock"]

FROM ghcr.io/kartverket/emudhra-images/jdk/dockerhub:jdk-25.0-alpine3.24@sha256:3b9c9aa8f9cd11b2660bace83082b223abc6923a9fc54bb7e08c9f122565db72
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

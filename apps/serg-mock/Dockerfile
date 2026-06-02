FROM eclipse-temurin:25-jdk-alpine@sha256:30d9f87d702c2c1c601ed0d31e0c88ea1ea474ee7676cda7b7a59e759181c4dd AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :apps:serg-mock:installDist --no-daemon

FROM eclipse-temurin:25-jre-alpine@sha256:c707c0d18cb9e8556380719f80d96a7529d0746fbb42143893949b98ed2f8943
WORKDIR /app
COPY --from=build /workspace/apps/serg-mock/build/install/serg-mock /app
EXPOSE 8084
ENTRYPOINT ["/app/bin/serg-mock"]

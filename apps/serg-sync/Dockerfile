FROM eclipse-temurin:23-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :apps:serg-sync:installDist --no-daemon

FROM eclipse-temurin:23-jre
WORKDIR /app
COPY --from=build /workspace/apps/serg-sync/build/install/serg-sync /app
EXPOSE 8080
ENTRYPOINT ["/app/bin/serg-sync"]

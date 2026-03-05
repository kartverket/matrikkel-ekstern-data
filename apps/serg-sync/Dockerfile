FROM eclipse-temurin:23-jdk AS build
WORKDIR /workspace

# Laster inn minimalt av filer for å få caching av gradle inn.
COPY gradlew gradlew.bat settings.gradle.kts gradle.properties gradle ./
RUN ./gradlew help --no-daemon

COPY . .
RUN ./gradlew :apps:serg-sync:installDist --no-daemon

FROM eclipse-temurin:23-jre
WORKDIR /app
COPY --from=build /workspace/apps/serg-sync/build/install/serg-sync /app
EXPOSE 8080
ENTRYPOINT ["/app/bin/serg-sync"]

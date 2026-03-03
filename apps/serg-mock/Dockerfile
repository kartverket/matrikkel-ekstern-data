FROM gradle:8.7-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :apps:serg-mock:installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/apps/serg-mock/build/install/serg-mock /app
EXPOSE 8084
ENTRYPOINT ["/app/bin/serg-mock"]

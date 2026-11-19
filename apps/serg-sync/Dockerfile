FROM dhi.io/eclipse-temurin:25.0.5.8-alpine@sha256:f3263435e20c3521197b318a3cd06f7fd6be2380d454e52c964576b1b38047c3 AS runtime

COPY apps/serg-sync/build/install/serg-sync/lib /app/lib

EXPOSE 8090

ENTRYPOINT ["java", "-cp", "/app/lib/*", "no.kartverket.matrikkel.MainKt"]
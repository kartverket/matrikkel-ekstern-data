FROM dhi.io/eclipse-temurin:27.0.1.1-alpine3.24@sha256:17cb4b82e960047fc0d04ed1247803128375305cb2b5e264237e7b200354975b AS runtime

COPY apps/freg-sync/build/install/freg-sync/lib /app/lib

EXPOSE 8090

ENTRYPOINT ["java", "-cp", "/app/lib/*", "no.kartverket.matrikkel.MainKt"]

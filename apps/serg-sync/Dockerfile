FROM eclipse-temurin:25-jre-alpine@sha256:c707c0d18cb9e8556380719f80d96a7529d0746fbb42143893949b98ed2f8943

COPY apps/serg-sync/build/install/serg-sync /app

USER nonroot
EXPOSE 8081

ENTRYPOINT ["/app/bin/serg-sync"]
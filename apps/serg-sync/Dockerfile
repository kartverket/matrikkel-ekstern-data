FROM dhi.io/eclipse-temurin:25.0.5.8-alpine3.24@sha256:8da329a70f7794dd59467841e7476fd96a22fa5d3146c36811d3589b5205a5d0 AS runtime

COPY apps/serg-sync/build/install/serg-sync/lib /app/lib

EXPOSE 8090

ENTRYPOINT ["java", "-cp", "/app/lib/*", "no.kartverket.matrikkel.MainKt"]
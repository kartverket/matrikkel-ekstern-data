FROM dhi.io/eclipse-temurin:25.0.5.8-alpine3.24@sha256:0e240b0c3747dc0508862cb00758eae4188310651d3428d27df0ca9e27f64873 AS runtime

COPY apps/brreg-sync/build/install/brreg-sync/lib /app/lib

EXPOSE 8090

ENTRYPOINT ["java", "-cp", "/app/lib/*", "no.kartverket.matrikkel.MainKt"]

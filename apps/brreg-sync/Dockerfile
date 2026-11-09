FROM dhi.io/eclipse-temurin:27.0.1.1-alpine3.24@sha256:89798eda4965ed3df1c3bee3a9c385de12e8b2bf55350a728a677a116f1c0fc5 AS runtime

COPY apps/brreg-sync/build/install/brreg-sync/lib /app/lib

EXPOSE 8090

ENTRYPOINT ["java", "-cp", "/app/lib/*", "no.kartverket.matrikkel.MainKt"]

FROM dhi.io/eclipse-temurin:25.0.5.8-alpine@sha256:9b972a8850dbb7baf0b6fa0b0ac01439d4b95ce5ec2e344b103c89b49fa31456 AS runtime

COPY apps/brreg-sync/build/install/brreg-sync/lib /app/lib

EXPOSE 8090

ENTRYPOINT ["java", "-cp", "/app/lib/*", "no.kartverket.matrikkel.MainKt"]

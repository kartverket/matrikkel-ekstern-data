FROM dhi.io/eclipse-temurin:25.0.5.8.1.2-alpine@sha256:f7e4086e2217b2804f273843311aceb8ff8a4a8e774e363171c7ae727d03fb29 AS runtime

COPY apps/serg-sync/build/install/serg-sync/lib /app/lib

EXPOSE 8090

ENTRYPOINT ["java", "-cp", "/app/lib/*", "no.kartverket.matrikkel.MainKt"]
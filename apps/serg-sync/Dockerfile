FROM ghcr.io/kartverket/emudhra-images/jre/dockerhub:jre-25.0-alpine3.24@sha256:a1a2a58677616ad8d737329c6c00e93d9a3c45661a0effc2bf7a93924c0b76fb

COPY apps/serg-sync/build/install/serg-sync /app

EXPOSE 8090

ENTRYPOINT ["/app/bin/serg-sync"]
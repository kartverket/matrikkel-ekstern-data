FROM ghcr.io/kartverket/emudhra-images/jre/dockerhub:jre-25.0-alpine3.24@sha256:2d2e8939adc7327871075062d4cca2dbd8da7542d903861dc9d0b84c96b3a722

COPY apps/serg-sync/build/install/serg-sync /app

EXPOSE 8090

ENTRYPOINT ["/app/bin/serg-sync"]
FROM eclipse-temurin:25-jre-alpine@sha256:3c0a9084927a221ccd1d007fcaf614465672c0af37aaa834c5184483afe56d61

RUN addgroup -g 150 -S apprunner \
 && adduser -u 150 -S apprunner -G apprunner

COPY --chown=150:150 apps/serg-sync/build/install/serg-sync /app

USER apprunner
EXPOSE 8081

ENTRYPOINT ["/app/bin/serg-sync"]
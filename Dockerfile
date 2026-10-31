# Runtime-only image from a locally-built jar — the same shape every service on this cluster uses.
# See deploy/build-image.sh; the jar is built outside so the image stays small and the build fast.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S -G app -u 10001 app
WORKDIR /opt/app
COPY --chown=app:app target/space-service-*.jar app.jar
# The container runs with a read-only root filesystem and only /tmp is writable, so the JVM has to
# be told where to put its scratch files. The heap defaults to a quarter of the container limit,
# which leaves most of it unused; 75% gives the in-memory database room. And a JVM out of memory
# limps on half-dead, answering nothing while the liveness probe may still pass — exiting hands it
# to Kubernetes to restart instead.
ENV SERVER_PORT=8122 \
    JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=/tmp -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
USER app
EXPOSE 8122
ENTRYPOINT ["java", "-jar", "app.jar"]

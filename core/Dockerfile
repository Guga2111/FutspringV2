# Futspring API image. Three stages: build (Maven, dependencies cached in their own layer), training (records the
# JVM's AOT cache on the same JRE as runtime) and runtime (non-root, layered jar, AOT cache, health check).

# ---- build ----
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace
# Dependencies first: this layer is reused while pom.xml doesn't change
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/*.jar extract --layers --application-filename app.jar --destination extracted

# ---- training: AOT cache (JEP 483/514) ----
# Same JRE and same /app layout as runtime, or the JVM ignores the cache. The context is refreshed and the JVM exits
# (spring.context.exit=onRefresh) without a database: Flyway off, no schema validation, no JDBC metadata lookup (so
# the dialect, normally detected from the connection, is named here).
FROM eclipse-temurin:25-jre-noble AS training
WORKDIR /app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
RUN DB_URL=jdbc:postgresql://localhost:5432/training DB_USERNAME=training DB_PASSWORD=training \
    JWT_SECRET=aot-training-secret-not-used-at-runtime-0123456789 \
    java -XX:AOTCacheOutput=app.aot -Dspring.context.exit=onRefresh -jar app.jar \
        --spring.flyway.enabled=false \
        --spring.jpa.hibernate.ddl-auto=none \
        --spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
        --spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect \
        --app.uploads.dir=/tmp/uploads

# ---- runtime ----
FROM eclipse-temurin:25-jre-noble
RUN (command -v curl >/dev/null || (apt-get update \
        && apt-get install -y --no-install-recommends curl \
        && rm -rf /var/lib/apt/lists/*)) \
    && groupadd --system --gid 10001 futspring \
    && useradd --system --uid 10001 --gid futspring --no-create-home --shell /usr/sbin/nologin futspring \
    && mkdir -p /app/uploads \
    && chown futspring:futspring /app/uploads
WORKDIR /app
# Least to most often changed, so a code change only replaces the last layers
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
COPY --from=training /app/app.aot ./
USER futspring
# Heap sized from the container's memory limit; an OOM restarts the container instead of leaving a broken JVM
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -XX:AOTCache=/app/app.aot"
EXPOSE 8080
# Readiness on the internal management port (never published): Flyway migrated and Hibernate validated the schema
HEALTHCHECK --interval=10s --timeout=3s --start-period=90s --retries=3 \
    CMD curl -fsS http://localhost:9091/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]

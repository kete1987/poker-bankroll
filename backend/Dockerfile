# syntax=docker/dockerfile:1

# ---- Build: compile the jar and split it into layers ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Dependencies first, so they stay cached while only the sources change.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests run in CI (they need Docker for Testcontainers), not in the image build.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
    && cp target/poker-bankroll-api-*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---- Runtime: JRE only, non-root ----
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app

# Least to most frequently changing layers.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null http://localhost:8080/api/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "application.jar"]

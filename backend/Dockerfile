# syntax=docker/dockerfile:1

# ---- Build: compile the jar and split it into layers ----
# The jar does not depend on the CPU architecture: build it once, natively, for every target
# platform instead of compiling under emulation.
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Dependencies first, so they stay cached while only the sources change.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Version reported by the app (build-info, OpenAPI); defaults to the one in pom.xml.
ARG APP_VERSION
# Tests run in CI (they need Docker for Testcontainers), not in the image build.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests ${APP_VERSION:+-Drevision=$APP_VERSION} \
    && cp target/poker-bankroll-api-*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# JDK modules used by the app and its dependencies, found by static analysis. Recomputed on every
# build, so a new dependency that needs another module gets it; the runtime stage below is only
# rebuilt when this list changes.
RUN jdeps --ignore-missing-deps --print-module-deps --multi-release 25 --recursive \
        --class-path "$(find extracted -name '*.jar' ! -name application.jar | paste -sd:)" \
        extracted/application/application.jar > jdk-modules.txt \
    && cat jdk-modules.txt

# ---- Java runtime: only the modules the app needs, linked with jlink ----
# Runs for the TARGET platform, not $BUILDPLATFORM: a linked runtime contains native code, so it is
# specific to the CPU architecture and to the C library. The Alpine JDK is built against musl, like
# the final base. It has no jmods/: jlink links from its own run-time image (JEP 493).
FROM eclipse-temurin:25-jdk-alpine AS jre
COPY --from=build /workspace/jdk-modules.txt /tmp/
# Modules added by hand, which jdeps cannot see because they are only used through reflection,
# service loading or locale lookup:
# - jdk.localedata (en and es only): number and date formats of the Spanish messages; java.base
#   only carries English locale data.
# - jdk.management: com.sun.management extensions of the JVM MXBeans, read reflectively by the
#   Actuator/Micrometer JVM and process metrics.
# Not needed: jdk.crypto.ec (empty since JDK 22, elliptic curves are in java.base), jdk.zipfs and
# jdk.charsets (the app only reads jars and UTF-8), jdk.naming.dns (host names resolve through
# java.base).
# --generate-cds-archive recreates the default CDS archive of the JDK, which keeps startup fast.
# No application AOT cache (JEP 483/514): its training run needs the app started, and the app does
# not start without PostgreSQL (Flyway, Hibernate schema validation).
RUN jlink \
        --add-modules "$(cat /tmp/jdk-modules.txt),jdk.localedata,jdk.management" \
        --include-locales=en,es \
        --strip-debug --no-man-pages --no-header-files --compress=zip-6 \
        --generate-cds-archive \
        --output /jre

# ---- Runtime: Alpine + the linked runtime, non-root ----
FROM alpine:3.24
LABEL org.opencontainers.image.source="https://github.com/kete1987/poker-bankroll"
# LANG as in the Temurin images: UTF-8 file names and console, default locale en_US. No tzdata or
# CA certificates needed: the JDK has its own time zone database (TZ=Europe/Madrid works) and
# truststore, and the API makes no outgoing TLS connections.
ENV JAVA_HOME=/opt/java/openjdk \
    PATH="/opt/java/openjdk/bin:$PATH" \
    LANG=en_US.UTF-8
COPY --from=jre /jre /opt/java/openjdk
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

# wget comes with Alpine's busybox.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null http://localhost:8080/api/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "application.jar"]

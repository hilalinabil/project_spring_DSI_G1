# Standard TicketFlow service image. Build context: repository root.
#   docker build -f services/config-server/Dockerfile .
# Every service uses this same template; only SERVICE and PORT differ.

# ---------- 1. build stage: JDK 21 + Maven wrapper ----------
FROM eclipse-temurin:21.0.9_10-jdk-noble AS build
WORKDIR /workspace

# Maven wrapper, parent POM and EVERY module POM (the reactor needs all of them).
COPY mvnw ./
COPY .mvn/ .mvn/
COPY pom.xml ./
COPY services/config-server/pom.xml    services/config-server/
COPY services/discovery-server/pom.xml services/discovery-server/
COPY services/api-gateway/pom.xml      services/api-gateway/
COPY services/customer-service/pom.xml services/customer-service/
COPY services/event-service/pom.xml    services/event-service/

# Dependencies in their own layer: only re-downloaded when a POM changes.
RUN ./mvnw -B -ntp -pl services/config-server -am dependency:go-offline

# Sources of this module only, then package.
COPY services/config-server/src services/config-server/src
RUN ./mvnw -B -ntp -pl services/config-server -am package -DskipTests

# ---------- 2. run stage: JRE 21 only ----------
FROM eclipse-temurin:21.0.9_10-jre-noble

# Dedicated non-root user (fixed UID/GID; 1000 is already taken by "ubuntu").
RUN groupadd --system --gid 10001 ticketflow \
 && useradd --system --uid 10001 --gid ticketflow --no-create-home --shell /usr/sbin/nologin ticketflow

WORKDIR /app
COPY --from=build --chown=ticketflow:ticketflow /workspace/services/config-server/target/config-server-*.jar app.jar

USER ticketflow:ticketflow
EXPOSE 8888

# curl ships with the eclipse-temurin noble image: nothing to install.
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
  CMD curl -fsS http://localhost:8888/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]

# syntax=docker/dockerfile:1
# Build context is the repo root (see compose.yaml) so the Maven reactor can
# see the aggregator pom and every module's pom.xml.
# --- Stage 1: build the application ---
FROM maven:3.9-eclipse-temurin-25-noble AS build

WORKDIR /workspace

# The reactor pom lists both modules, so both module poms must be present to
# resolve the reactor - even though only order-service is actually built.
COPY pom.xml .
COPY order-service/pom.xml order-service/pom.xml
COPY inventory-service/pom.xml inventory-service/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -B -pl order-service dependency:go-offline

COPY order-service/src order-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -pl order-service package -DskipTests

# --- Stage 2: run the application ---
FROM eclipse-temurin:25-jre-noble

WORKDIR /app

# wget is not guaranteed present on the minimal jre-noble base; install it
# explicitly so the HEALTHCHECK below can rely on it.
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*

RUN useradd --system --create-home --shell /usr/sbin/nologin appuser
COPY --from=build /workspace/order-service/target/*.jar app.jar
RUN chown appuser:appuser app.jar
USER appuser

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]

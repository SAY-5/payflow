# syntax=docker/dockerfile:1.7

# ---- builder ---------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml ./
RUN mvn -B dependency:go-offline -q
COPY src ./src
RUN mvn -B -q -DskipTests package \
 && mv target/payflow-*.jar /build/app.jar

# ---- runner ----------------------------------------------------------------
FROM eclipse-temurin:21-jre-noble AS runner
WORKDIR /app

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

RUN useradd --system --uid 10001 --no-create-home payflow \
 && chown -R payflow:payflow /app
USER payflow

COPY --from=builder --chown=payflow:payflow /build/app.jar /app/app.jar

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]

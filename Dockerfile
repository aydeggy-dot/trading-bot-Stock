# Multi-stage build for NGX Trading Bot
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

FROM mcr.microsoft.com/playwright/java:v1.41.0-jammy
WORKDIR /app

# Install JDK 21
RUN apt-get update && apt-get install -y wget && \
    wget -q https://download.eclipse.org/adoptium/21/jdk/x64/linux/hotspot/latest/OpenJDK21U-jdk_x64_linux_hotspot.tar.gz -O /tmp/jdk21.tar.gz && \
    mkdir -p /opt/java && tar -xzf /tmp/jdk21.tar.gz -C /opt/java --strip-components=1 && \
    rm /tmp/jdk21.tar.gz && apt-get clean

ENV JAVA_HOME=/opt/java
ENV PATH="$JAVA_HOME/bin:$PATH"

# Create directories for persistent data
RUN mkdir -p /app/screenshots /app/data /app/logs

# Copy built JAR
COPY --from=builder /app/target/*.jar app.jar

# Non-root user for security
RUN groupadd -r trader && useradd -r -g trader -d /app trader
RUN chown -R trader:trader /app
USER trader

# Health check
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", \
    "-XX:+UseG1GC", \
    "-XX:MaxGCPauseMillis=200", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:HeapDumpPath=/app/logs", \
    "-Djava.awt.headless=true", \
    "-jar", "app.jar"]

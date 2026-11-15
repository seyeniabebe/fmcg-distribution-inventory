# Stage 1: Build application with Maven
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

# Copy Maven dependencies & source
COPY pom.xml .
RUN apt-get update && apt-get install -y maven && mvn dependency:go-offline -B || true

COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal hardened runtime image
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Install curl for healthcheck
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# Create dedicated non-root user and persistent screenshots directory
RUN groupadd -r dmsgroup && useradd -r -g dmsgroup dmsuser && \
    mkdir -p /var/data/screenshots /app/uploads && \
    chown -R dmsuser:dmsgroup /var/data/screenshots /app

# Copy built JAR from build stage
COPY --from=build /app/target/fmcg-dms-*.jar /app/app.jar
RUN chown dmsuser:dmsgroup /app/app.jar

USER dmsuser

ENV PORT=8080
ENV UPLOAD_DIR=/var/data/screenshots
ENV SESSION_COOKIE_SECURE=true

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:${PORT:-8080}/actuator/health || exit 1

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]


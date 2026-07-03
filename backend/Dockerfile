# Stage 1: Build the Spring Boot application
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Copy Maven wrapper and POM for dependency caching
COPY mvnw mvnw.cmd ./
COPY .mvn/ .mvn/
COPY pom.xml ./

# Download dependencies (cached layer)
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source code
COPY src/ src/

# Build the application (skip tests for Docker build)
RUN ./mvnw package -B -DskipTests

# Stage 2: Run with minimal JRE
FROM eclipse-temurin:21-jre

WORKDIR /app

# Create non-root user for security
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copy the built JAR from the build stage
COPY --from=build /app/target/*.jar app.jar

# Create directories for file uploads and logs
RUN mkdir -p /app/uploads /app/logs && chown -R appuser:appuser /app

USER appuser

EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]

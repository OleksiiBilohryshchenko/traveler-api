# ============================
# Stage 1: Build the application
# ============================
FROM maven:3.9.6-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom.xml and resolve dependencies first (layer caching optimization)
COPY pom.xml .
RUN mvn -q -e -B dependency:go-offline

# Copy project sources and build
COPY src ./src
RUN mvn -q -e -B package -DskipTests

# ============================
# Stage 2: Runtime image
# ============================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy built JAR
COPY --from=builder /app/target/*.jar app.jar

# Create non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Expose application port
EXPOSE 4567

# Start application
ENTRYPOINT ["java", "-jar", "app.jar"]

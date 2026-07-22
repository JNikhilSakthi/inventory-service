# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM maven:3.9.8-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy POMs first so Maven can resolve the reactor and cache dependencies as a separate layer.
COPY pom.xml .
COPY inventory-service/pom.xml inventory-service/pom.xml
COPY external-mock-service/pom.xml external-mock-service/pom.xml
RUN mvn -q -pl inventory-service -am dependency:go-offline -B || true

COPY inventory-service/src inventory-service/src
RUN mvn -q -pl inventory-service -am package -DskipTests -B

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build /workspace/inventory-service/target/inventory-service.jar app.jar
USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

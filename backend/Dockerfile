# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk-jammy@sha256:02eecc58b9c7cb20e55765c7bcdcdee0c6d210440aca1b0a1fbd81ecfe8e772e AS build
WORKDIR /app

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src src
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:25-jre-jammy@sha256:20a695e74d47fb29cda1cbad5d9ee6cfad4ac6e88a8e048ed6265cede1e71f5e
WORKDIR /app

RUN useradd --system --uid 1001 spring
COPY --from=build /app/target/*.jar app.jar
USER spring

# Explicit safety net: prod hardening (forwarded-headers trust, springdoc disablement, log level,
# CORS origins) all live behind the prod profile. Without this, a deploy that forgets to set
# SPRING_PROFILES_ACTIVE externally silently boots with dev security posture instead of failing.
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk-jammy@sha256:f122992af75e61d87892f8a37c60f7cfa498b18748c1c9f8563da9a3b1893278 AS build
WORKDIR /app

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src src
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:25-jre-jammy@sha256:5bd5dbe00f40ea149de434a75029713765a2912cfc1fd770cc7c7aff007384ea
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

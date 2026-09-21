# syntax=docker/dockerfile:1

FROM eclipse-temurin:25-jdk-jammy@sha256:0cbcd13ae550a9c8334d5ef4483a4247625f47067f16127e28638fdafaeb35ea AS build
WORKDIR /app

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src src
RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:25-jre-jammy@sha256:abed22bb0186ab4554c339fa41e0a361daed337b938929c95a1f2e4a228c1935
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

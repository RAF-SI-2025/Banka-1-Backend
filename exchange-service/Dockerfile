# ===== Build Stage =====
FROM gradle:8.14.3-jdk21-alpine AS builder

WORKDIR /app

COPY exchange-service ./exchange-service
COPY company-observability-starter ./company-observability-starter
COPY security-lib ./security-lib

WORKDIR /app/exchange-service

RUN gradle build --no-daemon -x test -x checkstyleMain -x checkstyleTest

# ===== Runtime Stage =====
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl

WORKDIR /app

COPY --from=builder /app/exchange-service/build/libs/*.jar app.jar

ARG SERVER_PORT=8085
EXPOSE ${SERVER_PORT}

ENTRYPOINT ["java","-jar","app.jar"]

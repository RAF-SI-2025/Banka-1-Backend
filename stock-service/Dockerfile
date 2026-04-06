FROM gradle:8.14.3-jdk21-alpine AS builder

WORKDIR /app

COPY stock-service ./stock-service
COPY company-observability-starter ./company-observability-starter
COPY security-lib ./security-lib

WORKDIR /app/stock-service

RUN gradle build --no-daemon -x test -x checkstyleMain -x checkstyleTest

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl

WORKDIR /app

COPY --from=builder /app/stock-service/build/libs/*.jar app.jar

ARG SERVER_PORT=8090
EXPOSE ${SERVER_PORT}

ENTRYPOINT ["java","-jar","app.jar"]

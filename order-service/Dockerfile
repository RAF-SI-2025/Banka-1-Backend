FROM gradle:8.14.3-jdk21-alpine AS builder

WORKDIR /app

COPY order-service ./order-service
COPY security-lib ./security-lib
COPY company-observability-starter ./company-observability-starter

WORKDIR /app/order-service

RUN gradle build --no-daemon -x test -x checkstyleMain -x checkstyleTest

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl

WORKDIR /app

COPY --from=builder /app/order-service/build/libs/*.jar app.jar

ARG SERVER_PORT=8088
EXPOSE ${SERVER_PORT}

ENTRYPOINT ["java","-jar","app.jar"]

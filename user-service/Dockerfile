FROM gradle:8.14.3-jdk21-alpine AS builder

WORKDIR /app

COPY user-service ./user-service
COPY security-lib ./security-lib
COPY company-observability-starter ./company-observability-starter

WORKDIR /app/user-service

RUN gradle build --no-daemon -x test -x checkstyleMain -x checkstyleTest

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /app/user-service/build/libs/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java","-jar","app.jar"]
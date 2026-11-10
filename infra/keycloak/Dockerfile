FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY sms-authenticator/pom.xml .
RUN mvn -q dependency:go-offline
COPY sms-authenticator/src src
RUN mvn -q package

FROM quay.io/keycloak/keycloak:26.8.1
COPY --from=build /src/target/keycloak-vonage-otp-1.0.0.jar /opt/keycloak/providers/

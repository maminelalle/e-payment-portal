# Image de production : compilation Maven, puis exécution sur un JRE 21 minimal, sans droits root.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 sh mvnw -q -B dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 sh mvnw -q -B package -DskipTests && cp target/*.jar /app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /app.jar app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.awt.headless=true"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -pl notification-service -am package -DskipTests

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/notification-service/target/notification-service.jar app.jar
USER app
EXPOSE 8085
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","app.jar"]

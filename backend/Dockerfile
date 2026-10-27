FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 spacepick
COPY --from=build /app/build/libs/space-pick-0.1.0.jar app.jar
USER spacepick
EXPOSE 8081
ENTRYPOINT ["java","-jar","app.jar"]
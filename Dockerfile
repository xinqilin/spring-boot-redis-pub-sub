# ---- build ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew
COPY src ./src
RUN ./gradlew clean bootJar -x test --no-daemon

# ---- runtime ----
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

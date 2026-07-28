# Thin runtime image: the boot jar is built on the host (see scripts/run-local.sh) and copied in.
# Keeps the image small and the build fast; for a from-scratch reproducible build, swap for a
# multi-stage Gradle build.
FROM eclipse-temurin:21-jre
WORKDIR /app
# Staged next to this Dockerfile by the run script (the Spring Boot fat jar).
COPY app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

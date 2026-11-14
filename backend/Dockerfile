# syntax=docker/dockerfile:1

# ---- Build: the executable jar, split into layers that change at different rates ----
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY config config
COPY src src
# Tests need Docker (Testcontainers) and run in CI; code checks too.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp package -DskipTests -Dcheckstyle.skip -Dpmd.skip \
 && cp target/freezer-backend-*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ---- Run ----
FROM eclipse-temurin:25-jre-noble
RUN useradd --system --uid 10001 --no-create-home freezer
WORKDIR /app
# Dependencies first: they change rarely, so rebuilt images reuse these layers.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
USER freezer
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# No development defaults in the image: the deployment must set SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME,
# SPRING_DATASOURCE_PASSWORD, SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUERURI and FREEZER_LABELS_SCANURL
# (src/main/resources/application-container.yml); the application refuses to start without them.
ENV SPRING_PROFILES_ACTIVE=container
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

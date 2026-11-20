# Builds the app into a small image. Used by docker-compose.yml; you can also run:
#   docker build -t inventory .
#
# Two stages: the first has the full JDK and Maven (via ./mvnw) to build the jar; the second only
# has a Java runtime and the jar, so the final image is smaller and contains no source or build tools.

# ---- Stage 1: build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# Copy only the build files first and download dependencies. Docker caches this layer, so later
# builds skip the download unless pom.xml changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Windows checkouts can give mvnw CRLF line endings, which Linux can't run; strip them
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests already run in CI (and with ./mvnw test); skip them here to keep image builds quick
RUN ./mvnw -B -q package -DskipTests

# ---- Stage 2: run ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Don't run the app as root inside the container
RUN groupadd --system app && useradd --system --gid app app

COPY --from=build /build/target/*.jar app.jar
USER app

# Same port as running locally (see application.properties)
EXPOSE 8081

# Database settings come from environment variables (DB_URL, DB_USERNAME, DB_PASSWORD),
# which docker-compose.yml sets. application.properties already reads them.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

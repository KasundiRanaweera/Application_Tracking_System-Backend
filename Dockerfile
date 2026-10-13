# ---- Build stage: official Maven image (no wrapper download needed) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

# ---- Runtime stage: JRE only ----
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN mkdir -p /data/uploads
ENV APP_UPLOAD_DIR=/data/uploads

COPY --from=build /app/target/talentbridge-ats-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-8080}"]

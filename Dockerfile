# Build React frontend
FROM node:26-bookworm-slim AS frontend-build

WORKDIR /app/frontend/client

COPY frontend/client/package*.json ./
RUN npm ci

COPY frontend/client/ ./
RUN npm run build

# Build Spring Boot backend
FROM eclipse-temurin:25-jdk AS backend-build

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src/ src/

COPY --from=frontend-build /app/frontend/client/dist/ frontend/client/dist/

RUN ./mvnw -B -DskipTests package

# Run complete application
FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=backend-build /app/target/smartpark-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar app.jar"]

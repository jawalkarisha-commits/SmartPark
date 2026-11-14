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

COPY backend/.mvn/ backend/.mvn/
COPY backend/mvnw backend/mvnw
COPY backend/pom.xml backend/pom.xml
RUN chmod +x backend/mvnw

COPY backend/src/ backend/src/
COPY database/ database/

COPY --from=frontend-build /app/frontend/client/dist/ frontend/client/dist/

RUN ./backend/mvnw -f backend/pom.xml -B -DskipTests package

# Run complete application
FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=backend-build /app/backend/target/smartpark-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -jar app.jar"]

# Build multi-stage — Microservice CIN Haïti
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apk add --no-cache maven && mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache tesseract-ocr tesseract-ocr-data-fra
COPY --from=build /app/target/cin-microservice-*.jar app.jar
ENV TESSERACT_DATA_PATH=/usr/share/tessdata
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

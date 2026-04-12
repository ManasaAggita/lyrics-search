FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/data ./data 
COPY --from=build /app/target/*.jar app.jar

RUN mkdir -p data/index 

CMD ["sh", "-c", "java -Dserver.port=${PORT:-10000} -jar app.jar"]

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/tenant-service-*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75.0","-jar","app.jar"]

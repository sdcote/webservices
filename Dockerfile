# Use a slim OpenJDK image as the base
FROM openjdk:21-jdk-slim

# Set the working directory inside the container
WORKDIR /app

# Copy the built JAR file into the container
# The JAR file is typically named artifactId-version.jar
# Replace 'webservices*.jar' with your actual JAR file name
COPY target/webservices*.jar app.jar

# Expose the port your Spring Boot application runs on (default is 8080)
EXPOSE 8080

# Command to run the application when the container starts
ENTRYPOINT ["java", "-jar", "app.jar"]

# Optional: Add metadata for the image
LABEL maintainer="Steve Cote <sdcote@gmail.com>"
LABEL description="Spring Boot Profile Service with Swagger/OpenAPI"
# Use an official OpenJDK runtime as a parent image
FROM openjdk:24-jdk-slim

# Install mysqldump
RUN apt-get update && apt-get install -y mariadb-client

# Set the working directory
WORKDIR /app

# Copy the application JAR file to the container
COPY target/myapp-jar-with-dependencies.jar /app/myapp.jar

# Set the environment variables

# Expose the port your application runs on
EXPOSE 8080

# Define the command to run the pplication
CMD ["java", "-jar", "myapp.jar"]
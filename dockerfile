# Stage 1: Build the application
FROM gradle:8.5-jdk17 AS builder
WORKDIR /home/gradle/project

# Copy all project files
COPY . .

# Make gradlew executable (just in case)
RUN chmod +x ./gradlew

# Clean and build the bootJar (skip tests for speed)
# No need to specify java.home – the image provides it
RUN ./gradlew clean :app:bootJar -x test --no-daemon 
# Stage 2: Run the application
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the built jar from the builder stage
COPY --from=builder /home/gradle/project/app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
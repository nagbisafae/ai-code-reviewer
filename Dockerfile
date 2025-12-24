FROM eclipse-temurin:17-jdk

WORKDIR /app

RUN apt-get update && apt-get install -y ca-certificates && update-ca-certificates

# 1. Copy the files
COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle gradle

# 2. FIX: Grant execute permission to the wrapper script
RUN chmod +x gradlew

# 3. Now this will work
RUN ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew build --no-daemon -x test
RUN cp build/libs/*[!plain].jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
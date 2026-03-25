# Estágio 1: build com Gradle e JDK 21, alinhado à stack do projeto
FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace/app

# Copia os arquivos de build antes do código para melhorar o cache
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .

COPY src src

RUN chmod +x ./gradlew \
    && ./gradlew --no-daemon clean bootJar

# Estágio 2: runtime enxuto com JRE 21
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY --from=build /workspace/app/build/libs/*.jar app.jar

RUN useradd --system --create-home spring

USER spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# Estágio 1: Build com Gradle e JDK 17
FROM eclipse-temurin:17-jdk-jammy as build

WORKDIR /workspace/app

# Copia os arquivos do Gradle Wrapper - Melhora o cache
COPY gradlew .
COPY gradle gradle

# Copia os arquivos de build do Gradle - Melhora o cache de dependências
COPY build.gradle .
COPY settings.gradle .
# Se você usa build.gradle.kts, descomente a linha abaixo e comente a de cima
# COPY build.gradle.kts .
# COPY settings.gradle.kts .

# Copia o código fonte da aplicação APÓS os arquivos de build
# Assim, o download de dependências é cacheado se só o código mudar
COPY src src

# Garante que o gradlew seja executável
RUN chmod +x ./gradlew

# Constrói a aplicação e gera o JAR executável usando o wrapper
# O comando bootJar já baixa as dependências se necessário
# --no-daemon evita deixar um processo Gradle rodando
RUN ./gradlew bootJar --no-daemon

# --- 

# Estágio 2: Imagem Final com JRE 17
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Copia o JAR da aplicação construído no estágio anterior
# O Gradle gera o JAR em build/libs/
COPY --from=build /workspace/app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","app.jar"] 
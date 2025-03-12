FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /app

# Copiar arquivos de dependências
COPY pom.xml .
COPY src ./src

# Executar build
RUN mvn clean package -DskipTests

# Imagem de produção
FROM eclipse-temurin:17-jre-alpine AS production

WORKDIR /app

# Copiar apenas o JAR da etapa de build
COPY --from=builder /app/target/*.jar app.jar

# Configurar usuário não-root
RUN addgroup -g 1001 -S appuser && \
    adduser -S appuser -u 1001 -G appuser && \
    chown -R appuser:appuser /app

USER appuser

# Expor porta da aplicação
EXPOSE 8080

# Healthcheck
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# Comando para iniciar a aplicação
ENTRYPOINT ["java", "-jar", "app.jar"] 
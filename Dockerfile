# ─── Stage 1: Build ────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS build

WORKDIR /app

# Copia o pom.xml e baixa dependências primeiro (cache de layers)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copia o código fonte e compila
COPY src ./src
RUN mvn clean package -DskipTests -B

# ─── Stage 2: Runtime ──────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Instala curl para healthcheck
RUN apk add --no-cache curl

# Copia o JAR do stage de build
COPY --from=build /app/target/*.jar app.jar

# Cria diretórios necessários
RUN mkdir -p /app/logs /app/uploads /app/tokens

# Expõe a porta da aplicação
EXPOSE 8080

# Configuração de timezone
ENV TZ=America/Sao_Paulo
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseG1GC"

# Usuário não-root para segurança
RUN addgroup -S appgroup && adduser -S appuser -G appgroup && \
    chown -R appuser:appgroup /app

USER appuser

# Healthcheck
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Entrypoint
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
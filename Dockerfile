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

# Instala curl para healthcheck (opcional)
RUN apk add --no-cache curl

# Copia o JAR do stage de build
COPY --from=build /app/target/*.jar app.jar

# Cria diretório para tokens (necessário para OAuth2)
RUN mkdir -p /app/tokens

# Expõe a porta da aplicação
EXPOSE 8080

# Configuração de timezone (opcional)
ENV TZ=America/Sao_Paulo

# Entrypoint com suporte a variáveis de ambiente
ENTRYPOINT ["java", "-jar", "app.jar"]

# Healthcheck (opcional)
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
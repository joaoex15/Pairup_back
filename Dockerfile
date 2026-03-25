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

# Copia o jar gerado
COPY --from=build /app/target/*.jar app.jar

# Porta da aplicação
EXPOSE 8080

# Variáveis de ambiente padrão (podem ser sobrescritas no docker-compose)
ENV SPRING_DATA_MONGODB_HOST=mongodb
ENV SPRING_DATA_MONGODB_PORT=27017
ENV SPRING_DATA_MONGODB_DATABASE=TINDER

ENTRYPOINT ["java", "-jar", "app.jar"]
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
# Produto de fuso unico (spec 5.9, decisao do dono 2026-09-05): sem isso a
# JVM roda em UTC enquanto o psicologo esta em UTC-3, e data_hora_consulta
# (hora de parede) e created_at/deleted_at/pago_em (LocalDateTime.now()) sao
# comparados como se fossem a mesma grandeza. Colunas continuam TIMESTAMP
# sem fuso, de proposito - nao converter para TIMESTAMPTZ.
ENV TZ=America/Sao_Paulo
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
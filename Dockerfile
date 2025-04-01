FROM openjdk:17-jdk-slim

WORKDIR /app

# Copiar o JAR do aplicativo
COPY target/mcp-server-0.1.0.jar app.jar

# Expor a porta que o serviço usa internamente
EXPOSE 3500

# Comando para executar o aplicativo
ENTRYPOINT ["java", "-jar", "app.jar"]

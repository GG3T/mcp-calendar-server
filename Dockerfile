FROM openjdk:17-jdk-slim

# Instalar ferramentas de diagnóstico
RUN apt-get update && apt-get install -y curl wget iputils-ping net-tools dnsutils && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copiar o JAR do aplicativo
COPY target/mcp-server-0.3.0.jar app.jar

# Expor a porta que o serviço usa internamente
EXPOSE 3500

# Verificação de saúde com curl
HEALTHCHECK --interval=30s --timeout=10s --retries=3 CMD curl -f http://localhost:3500/ping || exit 1

# Comando para executar o aplicativo com debug adicional
ENTRYPOINT ["java", "-Dspring.output.ansi.enabled=ALWAYS", "-jar", "app.jar"]

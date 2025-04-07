FROM openjdk:17-jdk-slim

# Adicionar um usuário não-root para segurança
RUN addgroup --system mcp && adduser --system --group mcp

# Instalar ferramentas de diagnóstico
RUN apt-get update && apt-get install -y \
    curl \
    wget \
    iputils-ping \
    net-tools \
    dnsutils \
    && rm -rf /var/lib/apt/lists/*

# Configurar diretório de trabalho
WORKDIR /app

# Criar diretórios para logs e ajustar permissões
RUN mkdir -p /app/logs && \
    mkdir -p /logs && \
    chown -R mcp:mcp /app && \
    chown -R mcp:mcp /logs

# Copiar o JAR do aplicativo
COPY target/mcp-server-0.28.0.jar app.jar

# Mudar propriedade para o usuário não-root
RUN chown -R mcp:mcp /app

# Mudar para o usuário não-root
USER mcp

# Expor a porta que o serviço usa internamente
EXPOSE 3500

# Verificação de saúde com curl - apontando para o actuator/health
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD curl -f http://localhost:3500/actuator/health || exit 1

# Configuração do JVM para desempenho e estabilidade
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/logs/"

# Comando para executar o aplicativo com configurações avançadas
ENTRYPOINT exec java $JAVA_OPTS \
    -Dspring.output.ansi.enabled=ALWAYS \
    -Dserver.port=3500 \
    -Dserver.tomcat.accesslog.directory=/app/logs \
    -Dapp.sse.timeout-millis=300000 \
    -Dapp.sse.heartbeat-interval-millis=30000 \
    -Dapp.sse.heartbeat-enabled=true \
    -Dapp.rate-limit.max-requests=60 \
    -Dlogging.level.root=INFO \
    -Dlogging.level.chatgm.com.mcp_server=INFO \
    -Dlogging.level.org.springframework.web=INFO \
    -jar app.jar

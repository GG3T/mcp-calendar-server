package chatgm.com.mcp_server.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.sse")
@Getter
@Setter
public class SseConfig {
    /**
     * Tempo máximo em milissegundos para uma conexão SSE antes do timeout
     * Default: 5 minutos (300000 ms)
     */
    private long timeoutMillis = 300000;
    
    /**
     * Intervalo em milissegundos para enviar heartbeats para conexões SSE
     * Default: 30 segundos (30000 ms)
     * Defina como 0 para desativar heartbeats
     */
    private long heartbeatIntervalMillis = 30000;
    
    /**
     * Flag para habilitar ou desabilitar completamente os heartbeats
     */
    private boolean heartbeatEnabled = true;
    
    /**
     * Intervalo em milissegundos para verificação de saúde das conexões
     * Default: 60 segundos (60000 ms)
     */
    private long healthCheckIntervalMillis = 60000;
}

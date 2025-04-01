package chatgm.com.mcp_server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Classe de referência para configuração SSE
 * 
 * Esta classe foi substituída por configuração baseada em YAML
 * Veja application.yml para a configuração atual
 */
@Configuration
public class SseConfigReference {

    @Value("${spring.ai.mcp.server.transport.sse.url:unknown}")
    private String sseUrl;

    @Value("${spring.ai.mcp.server.transport.sse.headers.email:}")
    private String email;

    // A configuração agora é feita via application.yml
}

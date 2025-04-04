package chatgm.com.mcp_server.config;

import chatgm.com.mcp_server.service.TokenManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tarefa programada para limpar associações de tokens antigas.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupTask {

    private final TokenManagerService tokenManagerService;
    
    @Value("${app.token.cleanup.max-age-minutes:30}")
    private int maxAgeMinutes;
    
    /**
     * Executa a limpeza de tokens antigos a cada 10 minutos
     */
    @Scheduled(fixedRateString = "${app.token.cleanup.interval-ms:600000}")
    public void cleanupTokens() {
        log.debug("Iniciando limpeza de associações de tokens antigas (max idade: {} minutos)", maxAgeMinutes);
        tokenManagerService.cleanupOldAssociations(maxAgeMinutes);
    }
}
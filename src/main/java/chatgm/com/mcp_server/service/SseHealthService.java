package chatgm.com.mcp_server.service;

import chatgm.com.mcp_server.config.SseConfig;
import chatgm.com.mcp_server.controller.SseController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Serviço para monitorar a saúde das conexões SSE
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SseHealthService {

    private final SseController sseController;
    private final SseConfig sseConfig;
    
    // Registro do último heartbeat por cliente
    private final Map<String, Long> lastClientActivity = new HashMap<>();
    
    /**
     * Envia heartbeats periódicos para manter conexões SSE ativas
     * Executa de acordo com o intervalo configurado em app.sse.heartbeatIntervalMillis
     */
    @Scheduled(fixedDelayString = "${app.sse.heartbeatIntervalMillis:30000}")
    public void sendHeartbeats() {
        if (!sseConfig.isHeartbeatEnabled() || sseConfig.getHeartbeatIntervalMillis() <= 0) {
            return; // Heartbeats desativados
        }
        
        int clientCount = sseController.getConnectedClientsCount();
        if (clientCount == 0) {
            return; // Nenhum cliente conectado
        }
        
        // Registra em log com nível DEBUG (não aparecerá em produção com configuração INFO)
        if (log.isDebugEnabled()) {
            log.debug("Enviando heartbeat para {} clientes conectados", clientCount);
        }
        
        // Enviamos o heartbeat apenas para clientes que estão conectados há mais tempo
        sseController.sendHeartbeatToAllClients();
    }
    
    /**
     * Verifica periodicamente as conexões e remove aquelas inativas
     * Executa de acordo com o intervalo configurado em app.sse.healthCheckIntervalMillis
     */
    @Scheduled(fixedDelayString = "${app.sse.healthCheckIntervalMillis:60000}")
    public void checkConnections() {
        // Registra o horário atual para comparar com o último heartbeat
        long now = System.currentTimeMillis();
        long timeoutThreshold = now - sseConfig.getTimeoutMillis();
        
        // Verifica e fecha conexões inativas
        sseController.checkAndCloseInactiveConnections(timeoutThreshold);
        
        // Limpa o mapa de atividade para clientes desconectados
        lastClientActivity.entrySet().removeIf(entry -> 
            entry.getValue() < timeoutThreshold || !sseController.hasClient(entry.getKey()));
            
        // Log em nível INFO apenas periódico (a cada 10 minutos)
        if (now % 600000 < 1000) { // a cada 10 minutos (600.000 ms)
            int clientCount = sseController.getConnectedClientsCount();
            log.info("Status SSE: {} clientes ativos", clientCount);
        }
    }
    
    /**
     * Registra atividade de um cliente
     * @param token Token do cliente
     */
    public void recordClientActivity(String token) {
        lastClientActivity.put(token, System.currentTimeMillis());
    }
    
    /**
     * Formata o timestamp para exibição
     */
    private String formatTimestamp(long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp), 
                ZoneId.systemDefault());
        return dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}

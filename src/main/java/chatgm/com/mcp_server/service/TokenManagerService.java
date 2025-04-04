package chatgm.com.mcp_server.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serviço para gerenciar tokens e suas associações com IPs de clientes.
 * Esta classe serve como um registro central para mapear IPs para tokens
 * e garantir que o token correto seja utilizado durante as chamadas MCP.
 */
@Service
@Slf4j
public class TokenManagerService {

    // Mapeamento de IP para token
    private final Map<String, TokenInfo> ipToTokenMap = new ConcurrentHashMap<>();
    
    /**
     * Registra uma associação entre IP e token
     * @param ip IP do cliente
     * @param token Token de autenticação
     */
    public void registerIpTokenAssociation(String ip, String token) {
        if (ip == null || ip.trim().isEmpty() || token == null || token.trim().isEmpty()) {
            return;
        }
        
        TokenInfo tokenInfo = new TokenInfo(token, Instant.now());
        ipToTokenMap.put(ip, tokenInfo);
        log.debug("Associação registrada: IP {} -> Token {}", ip, token);
    }
    
    /**
     * Obtém o token associado a um IP
     * @param ip IP do cliente
     * @return Token associado ou null se não encontrado
     */
    public String getTokenForIp(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return null;
        }
        
        TokenInfo tokenInfo = ipToTokenMap.get(ip);
        if (tokenInfo != null) {
            // Atualiza o último acesso
            tokenInfo.setLastAccess(Instant.now());
            return tokenInfo.getToken();
        }
        return null;
    }
    
    /**
     * Remove uma associação de IP e token
     * @param ip IP do cliente
     */
    public void removeIpTokenAssociation(String ip) {
        if (ip != null && !ip.trim().isEmpty()) {
            ipToTokenMap.remove(ip);
            log.debug("Associação removida para IP: {}", ip);
        }
    }
    
    /**
     * Remove uma associação de token
     * @param token Token a ser removido
     */
    public void removeTokenAssociations(String token) {
        if (token != null && !token.trim().isEmpty()) {
            ipToTokenMap.entrySet().removeIf(entry -> entry.getValue().getToken().equals(token));
            log.debug("Associações removidas para token: {}", token);
        }
    }
    
    /**
     * Limpa associações antigas
     * @param maxAgeMinutes Idade máxima em minutos
     */
    public void cleanupOldAssociations(int maxAgeMinutes) {
        Instant threshold = Instant.now().minusSeconds(maxAgeMinutes * 60L);
        
        int removedCount = 0;
        for (Map.Entry<String, TokenInfo> entry : ipToTokenMap.entrySet()) {
            if (entry.getValue().getLastAccess().isBefore(threshold)) {
                ipToTokenMap.remove(entry.getKey());
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            log.info("Limpeza de associações: {} associações antigas removidas", removedCount);
        }
    }
    
    /**
     * Classe interna para armazenar informações do token
     */
    private static class TokenInfo {
        private final String token;
        private Instant lastAccess;
        
        public TokenInfo(String token, Instant lastAccess) {
            this.token = token;
            this.lastAccess = lastAccess;
        }
        
        public String getToken() {
            return token;
        }
        
        public Instant getLastAccess() {
            return lastAccess;
        }
        
        public void setLastAccess(Instant lastAccess) {
            this.lastAccess = lastAccess;
        }
    }
}
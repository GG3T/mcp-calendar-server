package chatgm.com.mcp_server.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Serviço centralizado para gerenciamento de tokens de autenticação
 */
@Service
@Slf4j
public class TokenService {

    private static final String TOKEN_ATTRIBUTE = "mcp-token";
    private static String lastActiveToken = null;

    private final TokenManagerService tokenManagerService;

    public TokenService(TokenManagerService tokenManagerService) {
        this.tokenManagerService = tokenManagerService;
    }

    /**
     * Extrai o token de autenticação a partir de várias fontes
     * @param request A requisição HTTP atual
     * @return O token encontrado ou null
     */
    public String extractToken(HttpServletRequest request) {
        if (request == null) {
            return getLastActiveToken();
        }

        // Prioridade 1: Header "token" (específico MCP)
        String token = request.getHeader("token");
        if (isValidToken(token)) {
            log.debug("Token extraído do header 'token'");
            updateLastActiveToken(token, request);
            return token;
        }
        
        // Prioridade 2: Header "Authorization"
        token = request.getHeader("Authorization");
        if (isValidToken(token)) {
            log.debug("Token extraído do header 'Authorization'");
            updateLastActiveToken(token, request);
            return token;
        }
        
        // Prioridade 3: Parâmetro de URL "token"
        token = request.getParameter("token");
        if (isValidToken(token)) {
            log.debug("Token extraído do parâmetro 'token'");
            updateLastActiveToken(token, request);
            return token;
        }
        
        // Prioridade 4: IP do cliente mapeado
        String clientIp = extractClientIp(request);
        token = tokenManagerService.getTokenForIp(clientIp);
        if (isValidToken(token)) {
            log.debug("Token extraído do mapeamento IP [{}] -> token", clientIp);
            return token;
        }
        
        // Prioridade 5: Último token ativo
        return getLastActiveToken();
    }
    
    /**
     * Extrai o token da requisição atual usando o contexto Spring
     * @return O token encontrado ou null
     */
    public String extractTokenFromCurrentRequest() {
        try {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes) {
                ServletRequestAttributes servletAttributes = (ServletRequestAttributes) attributes;
                HttpServletRequest request = servletAttributes.getRequest();
                
                // Verificar se já extraímos o token e salvamos como atributo
                String savedToken = (String) request.getAttribute(TOKEN_ATTRIBUTE);
                if (isValidToken(savedToken)) {
                    return savedToken;
                }
                
                // Extrair o token normalmente
                return extractToken(request);
            }
        } catch (Exception e) {
            log.error("Erro ao extrair token da requisição atual: {}", e.getMessage());
        }
        
        return getLastActiveToken();
    }
    
    /**
     * Salva o token como atributo da requisição
     * @param request A requisição HTTP
     * @param token O token a ser salvo
     */
    public void saveTokenAsAttribute(HttpServletRequest request, String token) {
        if (request != null && isValidToken(token)) {
            request.setAttribute(TOKEN_ATTRIBUTE, token);
        }
    }
    
    /**
     * Extrai o IP real do cliente, considerando proxies
     * @param request A requisição HTTP
     * @return O IP do cliente
     */
    public String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        
        String ip = request.getHeader("X-Forwarded-For");
        if (isValidToken(ip)) {
            return ip.split(",")[0].trim();
        }
        
        ip = request.getHeader("X-Real-IP");
        if (isValidToken(ip)) {
            return ip;
        }
        
        return request.getRemoteAddr();
    }
    
    private boolean isValidToken(String token) {
        return token != null && !token.trim().isEmpty();
    }
    
    private void updateLastActiveToken(String token, HttpServletRequest request) {
        if (isValidToken(token)) {
            lastActiveToken = token;
            
            // Registrar mapeamento IP -> token
            String clientIp = extractClientIp(request);
            if (clientIp != null) {
                tokenManagerService.registerIpTokenAssociation(clientIp, token);
            }
        }
    }
    
    private String getLastActiveToken() {
        return lastActiveToken;
    }
}
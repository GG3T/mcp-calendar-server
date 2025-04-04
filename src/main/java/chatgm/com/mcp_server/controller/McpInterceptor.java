package chatgm.com.mcp_server.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Interceptor simples para requisições MCP.
 * Captura o token do header "token" e o disponibiliza para toda a aplicação
 * através de uma variável estática.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class McpInterceptor implements HandlerInterceptor {

    private final SseController sseController;
    
    // Variável estática para armazenar o último token usado em requisições MCP
    public static String lastMcpToken = null;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Verificamos se é uma requisição MCP
        String requestUri = request.getRequestURI();
        if (requestUri.startsWith("/mcp/")) {
            // Tentamos obter o token do header
            String token = request.getHeader("token");
            
            if (token != null && !token.isEmpty()) {
                // Se encontramos, armazenamos para uso futuro
                lastMcpToken = token;
                log.debug("Token capturado de requisição MCP: {}", token);
            } else {
                // Tentamos outras fontes
                token = request.getParameter("token");
                if (token != null && !token.isEmpty()) {
                    lastMcpToken = token;
                    log.debug("Token capturado de parâmetro em requisição MCP: {}", token);
                } else {
                    token = request.getHeader("Authorization");
                    if (token != null && !token.isEmpty()) {
                        lastMcpToken = token;
                        log.debug("Token capturado de header Authorization em requisição MCP: {}", token);
                    }
                }
            }
        }
        
        return true;
    }
}
package chatgm.com.mcp_server.config;

import chatgm.com.mcp_server.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro que captura o token da requisição MCP e o define como atributo
 * para uso posterior pelas ferramentas.
 */
@Component
@RequiredArgsConstructor
@Order(1)
@Slf4j
public class McpTokenResolverFilter extends OncePerRequestFilter {

    private final TokenService tokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestUri = request.getRequestURI();
        
        // Intercepta todas as chamadas, mas dá prioridade para MCP
        boolean isMcpRequest = requestUri.startsWith("/mcp/");
        
        if (isMcpRequest) {
            // Para requisições MCP, fazemos log mais detalhado
            log.debug("Interceptando requisição MCP: {}", requestUri);
            
            // Processa a requisição MCP e extrai o token
            String token = tokenService.extractToken(request);
            
            if (token != null) {
                // Salva o token como atributo da requisição
                tokenService.saveTokenAsAttribute(request, token);
                log.debug("Token definido para requisição MCP");
            } else {
                // Log de depuração detalhado para ajudar a identificar o problema
                log.warn("Nenhum token encontrado para requisição MCP: {}", requestUri);
                log.debug("Headers da requisição MCP:");
                request.getHeaderNames().asIterator().forEachRemaining(header -> 
                    log.debug("  {}: {}", header, request.getHeader(header))
                );
            }
        } else {
            // Outras requisições também podem precisar do token
            String token = tokenService.extractToken(request);
            if (token != null) {
                tokenService.saveTokenAsAttribute(request, token);
            }
        }

        filterChain.doFilter(request, response);
    }
}
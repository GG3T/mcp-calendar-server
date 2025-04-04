package chatgm.com.mcp_server.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Filtro para implementar rate limiting baseado no IP do cliente
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private final Map<String, RequestCounter> requestCounts = new ConcurrentHashMap<>();
    
    @Value("${app.rate-limit.max-requests:30}")
    private int maxRequestsPerMinute;
    
    @Value("${app.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        if (!rateLimitEnabled) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Obtém o IP real do cliente usando X-Forwarded-For ou o IP direto
        String clientIp = getClientIp(request);
        String path = request.getRequestURI();
        
        // Ignora healthchecks e pings para rate limiting
        if (path.equals("/actuator/health") || path.equals("/ping")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Obtém ou cria um contador para este IP
        RequestCounter counter = requestCounts.computeIfAbsent(clientIp, ip -> new RequestCounter());
        
        // Verifica se excedeu o limite
        if (counter.incrementAndGet() > maxRequestsPerMinute) {
            log.warn("Rate limit excedido para IP: {} no path: {}", clientIp, path);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("Taxa de requisições excedida. Tente novamente em breve.");
            return;
        }
        
        // Continua a cadeia de filtros se estiver dentro do limite
        filterChain.doFilter(request, response);
    }
    
    /**
     * Obtém o IP real do cliente, considerando proxies
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Pega o primeiro IP na cadeia, que é o IP original do cliente
            return xForwardedFor.split(",")[0].trim();
        }
        // Tenta o header X-Real-IP
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        // Usa o IP remoto padrão
        return request.getRemoteAddr();
    }
    
    /**
     * Classe para contar requisições com reset automático após 1 minuto
     */
    private static class RequestCounter {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long resetTime;
        
        public RequestCounter() {
            resetTime = System.currentTimeMillis() + 60000; // Reset após 1 minuto
        }
        
        public int incrementAndGet() {
            long currentTime = System.currentTimeMillis();
            if (currentTime > resetTime) {
                // Reset o contador e o tempo se passou 1 minuto
                count.set(0);
                resetTime = currentTime + 60000;
            }
            return count.incrementAndGet();
        }
    }
}

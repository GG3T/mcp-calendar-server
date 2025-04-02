package chatgm.com.mcp_server.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller para verificar a saúde da aplicação e debugging.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @Value("${spring.ai.mcp.server.external-url:}")
    private String externalUrl;

    @Value("${server.port:3500}")
    private int serverPort;

    /**
     * Endpoint básico para verificar se a aplicação está funcionando.
     * Traefik pode usar isso para verificação de saúde.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", System.currentTimeMillis());
        
        Map<String, Object> details = new HashMap<>();
        details.put("externalUrl", externalUrl);
        details.put("serverPort", serverPort);
        details.put("javaVersion", System.getProperty("java.version"));
        details.put("osName", System.getProperty("os.name"));
        
        response.put("details", details);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Endpoint alternativo para verificação de saúde no contexto raiz
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return health();
    }

    /**
     * Endpoint para verificar os headers da requisição.
     * Útil para debugging de problemas de proxy.
     */
    @GetMapping("/headers")
    public ResponseEntity<Map<String, Object>> headers(HttpServletRequest request, 
                                                      @RequestHeader(value = "X-Custom-Header", required = false) String customHeader) {
        Map<String, Object> headers = new HashMap<>();
        
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.put(headerName, Collections.list(request.getHeaders(headerName)));
        }
        
        Map<String, Object> requestInfo = new HashMap<>();
        requestInfo.put("remoteAddr", request.getRemoteAddr());
        requestInfo.put("method", request.getMethod());
        requestInfo.put("requestURI", request.getRequestURI());
        requestInfo.put("queryString", request.getQueryString());
        requestInfo.put("protocol", request.getProtocol());
        requestInfo.put("serverName", request.getServerName());
        requestInfo.put("serverPort", request.getServerPort());
        requestInfo.put("scheme", request.getScheme());
        requestInfo.put("contextPath", request.getContextPath());
        requestInfo.put("servletPath", request.getServletPath());
        
        Map<String, Object> response = new HashMap<>();
        response.put("headers", headers);
        response.put("requestInfo", requestInfo);
        response.put("customHeader", customHeader);
        response.put("timestamp", LocalDateTime.now().toString());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Endpoint para simular erro 500
     */
    @GetMapping("/error500")
    public ResponseEntity<Object> simulateError() {
        throw new RuntimeException("Erro simulado para teste");
    }
}

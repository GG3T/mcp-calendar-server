package chatgm.com.mcp_server.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.web.filter.CommonsRequestLoggingFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Configuração para logging detalhado de requisições HTTP
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class RequestLoggingConfig {

    @Value("${app.request-logging.include-headers:true}")
    private boolean includeHeaders;

    @Value("${app.request-logging.include-payload:true}")
    private boolean includePayload;

    @Value("${app.request-logging.include-response:true}")
    private boolean includeResponse;

    @Value("${app.request-logging.max-payload-length:10000}")
    private int maxPayloadLength;

    // Lista de endpoints que devem ser ignorados no logging detalhado
    private static final Set<String> IGNORED_PATHS = new HashSet<>(Arrays.asList(
            "/ping",
            "/actuator/health",
            "/actuator/info",
            "/favicon.ico",
            "/error"
    ));

    // Lista de eventos SSE que devem ser ignorados no logging
    private static final Set<String> IGNORED_SSE_EVENTS = new HashSet<>(Arrays.asList(
            "ping",
            "heartbeat"
    ));

    /**
     * Configura o filtro de logging padrão do Spring (não será usado para SSE)
     */
    @Bean
    @ConditionalOnProperty(name = "app.request-logging.commons-logging", havingValue = "true", matchIfMissing = false)
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        CommonsRequestLoggingFilter loggingFilter = new CommonsRequestLoggingFilter();
        loggingFilter.setIncludeClientInfo(true);
        loggingFilter.setIncludeQueryString(true);
        loggingFilter.setIncludePayload(includePayload);
        loggingFilter.setMaxPayloadLength(maxPayloadLength);
        loggingFilter.setIncludeHeaders(includeHeaders);
        loggingFilter.setBeforeMessagePrefix("REQUEST: ");
        loggingFilter.setAfterMessagePrefix("RESPONSE: ");
        return loggingFilter;
    }

    /**
     * Registra o filtro de logging detalhado com alta prioridade
     */
    @Bean
    @ConditionalOnProperty(name = "app.request-logging.detailed", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<DetailedRequestLoggingFilter> detailedRequestLoggingFilter() {
        DetailedRequestLoggingFilter filter = new DetailedRequestLoggingFilter(
                includeHeaders, includePayload, includeResponse, maxPayloadLength);
                
        FilterRegistrationBean<DetailedRequestLoggingFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(filter);
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10); // Logo após o filtro de segurança
        
        // Aplicar a todos os caminhos
        registrationBean.addUrlPatterns("/*");
        
        return registrationBean;
    }
    
    /**
     * Classe de filtro para logging detalhado de requisições e respostas
     */
    public static class DetailedRequestLoggingFilter extends OncePerRequestFilter {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        
        private final boolean includeHeaders;
        private final boolean includePayload;
        private final boolean includeResponse;
        private final int maxPayloadLength;
        
        public DetailedRequestLoggingFilter(boolean includeHeaders, boolean includePayload, 
                                           boolean includeResponse, int maxPayloadLength) {
            this.includeHeaders = includeHeaders;
            this.includePayload = includePayload;
            this.includeResponse = includeResponse;
            this.maxPayloadLength = maxPayloadLength;
        }
        
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            
            // Não logar endpoints ignorados
            String path = request.getRequestURI();
            if (IGNORED_PATHS.contains(path)) {
                filterChain.doFilter(request, response);
                return;
            }
            
            // Para SSE, registramos apenas o início da conexão e os headers
            boolean isSseRequest = path.equals("/sse");
            if (isSseRequest) {
                // Captura os headers para logging antes de processar
                Map<String, List<String>> requestHeaders = new HashMap<>();
                if (includeHeaders) {
                    Collections.list(request.getHeaderNames()).forEach(headerName -> {
                        List<String> values = Collections.list(request.getHeaders(headerName));
                        requestHeaders.put(headerName, values);
                    });
                }
                
                StringBuilder logMessage = new StringBuilder();
                logMessage.append("\n---- SSE Connection Request ----\n");
                logMessage.append(String.format("[%s] Request: %s %s %s\n", 
                        formatter.format(LocalDateTime.now()),
                        request.getMethod(), 
                        request.getRequestURI(),
                        getQueryStringWithoutSensitiveInfo(request)));
                
                logMessage.append("Client: ").append(getClientIp(request)).append("\n");
                
                // Adiciona headers (filtrando senhas/tokens)
                if (includeHeaders) {
                    logMessage.append("Headers:\n");
                    requestHeaders.forEach((headerName, values) -> {
                        String headerValue = String.join(", ", values);
                        if (headerName.toLowerCase().contains("authorization") || 
                            headerName.toLowerCase().contains("token") ||
                            headerName.toLowerCase().contains("key")) {
                            headerValue = "******";
                        }
                        logMessage.append(String.format("  %s: %s\n", headerName, headerValue));
                    });
                }
                
                logMessage.append("---- End SSE Connection Request ----");
                log.info(logMessage.toString());
                
                filterChain.doFilter(request, response);
                return;
            }
            
            // Para outras requisições, usamos wrapper para capturar corpo
            ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
            ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
            
            long startTime = System.currentTimeMillis();
            LocalDateTime startDateTime = LocalDateTime.now();
            boolean isMultipart = request.getContentType() != null && 
                                 request.getContentType().startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);

            try {
                // Executa o filtro
                filterChain.doFilter(requestWrapper, responseWrapper);
            } finally {
                long duration = System.currentTimeMillis() - startTime;
                
                // Cria log detalhado para requisições normais
                if (log.isInfoEnabled()) {
                    StringBuilder logMessage = new StringBuilder();
                    logMessage.append("\n---- HTTP Request/Response ----\n");
                    logMessage.append(String.format("[%s] Request: %s %s %s\n", 
                            formatter.format(startDateTime),
                            request.getMethod(), 
                            request.getRequestURI(),
                            getQueryStringWithoutSensitiveInfo(request)));
                    
                    // Informações do cliente
                    logMessage.append("Client: ").append(getClientIp(request)).append("\n");
                    logMessage.append("User-Agent: ").append(request.getHeader("User-Agent")).append("\n");
                    
                    // Adiciona headers (filtrando senhas/tokens)
                    if (includeHeaders) {
                        logMessage.append("Request Headers:\n");
                        Collections.list(request.getHeaderNames()).forEach(headerName -> {
                            String headerValue = request.getHeader(headerName);
                            if (headerName.toLowerCase().contains("authorization") || 
                                headerName.toLowerCase().contains("token") ||
                                headerName.toLowerCase().contains("key") ||
                                headerName.toLowerCase().contains("secret") ||
                                headerName.toLowerCase().contains("password")) {
                                headerValue = "******";
                            }
                            logMessage.append(String.format("  %s: %s\n", headerName, headerValue));
                        });
                    }
                    
                    // Adiciona corpo da requisição se não for vazio e se estiver habilitado
                    if (includePayload && !isMultipart) {
                        String requestBody = getMessagePayload(requestWrapper);
                        if (requestBody != null && !requestBody.isEmpty()) {
                            logMessage.append("Request Body: \n");
                            if (requestBody.length() > maxPayloadLength) {
                                // Trunque corpos muito grandes
                                logMessage.append(requestBody, 0, maxPayloadLength).append("... [truncated]\n");
                            } else {
                                logMessage.append(requestBody).append("\n");
                            }
                        }
                    } else if (isMultipart) {
                        logMessage.append("Request Body: [multipart form data]\n");
                    }
                    
                    // Adiciona informações da resposta
                    logMessage.append(String.format("Response Status: %d (%dms)\n", 
                            responseWrapper.getStatus(), duration));
                    
                    // Adiciona headers de resposta
                    if (includeHeaders) {
                        logMessage.append("Response Headers:\n");
                        responseWrapper.getHeaderNames().forEach(headerName -> {
                            String headerValue = responseWrapper.getHeader(headerName);
                            logMessage.append(String.format("  %s: %s\n", headerName, headerValue));
                        });
                    }
                    
                    // Adiciona corpo da resposta se não for vazio e estiver habilitado
                    if (includeResponse) {
                        String responseBody = getMessagePayload(responseWrapper);
                        if (responseBody != null && !responseBody.isEmpty()) {
                            logMessage.append("Response Body: \n");
                            if (responseBody.length() > maxPayloadLength) {
                                // Trunque corpos muito grandes
                                logMessage.append(responseBody, 0, maxPayloadLength).append("... [truncated]\n");
                            } else {
                                logMessage.append(responseBody).append("\n");
                            }
                        }
                    }
                    
                    logMessage.append("---- End HTTP Request/Response ----");
                    
                    // Decide o nível de log baseado no status da resposta
                    int status = responseWrapper.getStatus();
                    if (status >= 500) {
                        log.error(logMessage.toString());
                    } else if (status >= 400) {
                        log.warn(logMessage.toString());
                    } else {
                        log.info(logMessage.toString());
                    }
                }
                
                // IMPORTANTE: Copia o conteúdo de volta para a resposta original
                responseWrapper.copyBodyToResponse();
            }
        }
        
        /**
         * Extrai o payload da requisição em formato String
         */
        private String getMessagePayload(ContentCachingRequestWrapper request) {
            byte[] buf = request.getContentAsByteArray();
            if (buf.length == 0) return "";
            
            int length = Math.min(buf.length, maxPayloadLength);
            try {
                return new String(buf, 0, length, request.getCharacterEncoding());
            } catch (UnsupportedEncodingException ex) {
                return "[Unknown encoding]";
            }
        }
        
        /**
         * Extrai o payload da resposta em formato String
         */
        private String getMessagePayload(ContentCachingResponseWrapper response) {
            byte[] buf = response.getContentAsByteArray();
            if (buf.length == 0) return "";
            
            int length = Math.min(buf.length, maxPayloadLength);
            
            try {
                String contentType = response.getContentType();
                // Para tipos binários, não imprimimos o conteúdo
                if (contentType != null && (
                        contentType.contains("image") || 
                        contentType.contains("pdf") || 
                        contentType.contains("octet-stream") ||
                        contentType.contains("zip") ||
                        contentType.contains("audio") ||
                        contentType.contains("video"))) {
                    return "[Binary content: " + buf.length + " bytes]";
                }
                
                return new String(buf, 0, length, StandardCharsets.UTF_8);
            } catch (Exception ex) {
                return "[Error reading response: " + ex.getMessage() + "]";
            }
        }
        
        /**
         * Obtém o query string, removendo informações sensíveis
         */
        private String getQueryStringWithoutSensitiveInfo(HttpServletRequest request) {
            String queryString = request.getQueryString();
            if (queryString == null) {
                return "";
            }
            
            // Filtra parâmetros sensíveis
            queryString = queryString.replaceAll("(?i)(token|key|auth|password|secret)=[^&]*", "$1=******");
            
            return "?" + queryString;
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
    }
}
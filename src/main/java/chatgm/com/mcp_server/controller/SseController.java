package chatgm.com.mcp_server.controller;

import chatgm.com.mcp_server.config.SseConfig;
import chatgm.com.mcp_server.service.AppointmentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SseController {

    private final AppointmentService appointmentService;
    private final SseConfig sseConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Armazena os emitters e o último timestamp de atividade
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<String, Long> lastActivityTime = new ConcurrentHashMap<>();
    
    // Mapeamento de IP para token
    private final Map<String, String> ipToTokenMap = new ConcurrentHashMap<>();
    
    // Variável estática para armazenar o último token ativo
    private static String lastActiveToken = null;
    
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * Obtém o token do usuário atual para ser usado em chamadas externas
     * @return token do usuário atualmente autenticado
     */
    public String getCurrentToken() {
        try {
            // Verificar se temos um token na requisição atual
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                
                // Verificar atributo mcp-token definido pelo filtro
                String mcpToken = (String) request.getAttribute("mcp-token");
                if (mcpToken != null && !mcpToken.isEmpty()) {
                    log.debug("Token obtido do atributo mcp-token: {}", mcpToken);
                    // Atualiza último token ativo e mapeamento IP -> token
                    lastActiveToken = mcpToken;
                    String clientIp = getClientIp(request);
                    ipToTokenMap.put(clientIp, mcpToken);
                    return mcpToken;
                }
                
                // Verificamos o header token (usado pelo MCP)
                String token = request.getHeader("token");
                if (token != null && !token.isEmpty()) {
                    log.debug("Token obtido do header 'token': {}", token);
                    lastActiveToken = token;
                    String clientIp = getClientIp(request);
                    ipToTokenMap.put(clientIp, token);
                    return token;
                }
                
                // Verificamos o header Authorization
                token = request.getHeader("Authorization");
                if (token != null && !token.isEmpty()) {
                    log.debug("Token obtido do header 'Authorization': {}", token);
                    lastActiveToken = token;
                    String clientIp = getClientIp(request);
                    ipToTokenMap.put(clientIp, token);
                    return token;
                }
                
                // Verificamos o parâmetro token na URL
                token = request.getParameter("token");
                if (token != null && !token.isEmpty()) {
                    log.debug("Token obtido do parâmetro 'token': {}", token);
                    lastActiveToken = token;
                    String clientIp = getClientIp(request);
                    ipToTokenMap.put(clientIp, token);
                    return token;
                }
                
                // Tentamos obter do IP do cliente
                String clientIp = getClientIp(request);
                token = ipToTokenMap.get(clientIp);
                if (token != null && !token.isEmpty()) {
                    log.debug("Token obtido do mapeamento IP->token para {}: {}", clientIp, token);
                    return token;
                }
                
                // Logs para depuração
                log.debug("Headers da requisição:");
                request.getHeaderNames().asIterator().forEachRemaining(header -> 
                    log.debug("  {}: {}", header, request.getHeader(header))
                );
            }
            
            // Se temos um último token ativo, usamos ele
            if (lastActiveToken != null && !lastActiveToken.isEmpty()) {
                log.debug("Usando último token ativo: {}", lastActiveToken);
                return lastActiveToken;
            }
            
            // Se temos algum emitter ativo, podemos usar o token dele
            if (!emitters.isEmpty()) {
                String firstToken = emitters.keySet().iterator().next();
                log.debug("Usando token do primeiro cliente conectado: {}", firstToken);
                return firstToken;
            }
            
            log.warn("Não foi possível determinar o token do usuário atual");
            return null;
        } catch (Exception e) {
            log.error("Erro ao obter token: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Endpoint SSE para estabelecer uma conexão persistente
     */
    @GetMapping(path = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "token", required = false) String tokenHeader,
            @RequestParam(value = "token", required = false) String tokenParam) {

        // Determina o token a ser usado para autenticação
        final String token;

        // Tentamos obter de todas as fontes possíveis
        if (tokenHeader != null && !tokenHeader.isEmpty()) {
            token = tokenHeader;
            log.debug("Token obtido do header token");
        } else if (authHeader != null && !authHeader.isEmpty()) {
            token = authHeader;
            log.debug("Token obtido do header Authorization");
        } else if (tokenParam != null && !tokenParam.isEmpty()) {
            token = tokenParam;
            log.debug("Token obtido do parâmetro token");
        } else {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            String clientIp = getClientIp(request);
            log.error("Tentativa de conexão sem token do IP: {}", clientIp);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Token não fornecido. Use o header token, Authorization ou o parâmetro token na URL");
        }

        // Armazenamos o token como o último ativo
        lastActiveToken = token;
        
        // Também mapeamos o IP -> token
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String clientIp = getClientIp(request);
        ipToTokenMap.put(clientIp, token);

        // Log
        log.info("Nova conexão SSE de IP: {} com token: {}", clientIp, token);

        // Configuração do emitter
        SseEmitter emitter = new SseEmitter(sseConfig.getTimeoutMillis());

        // Configuração para gerenciar o ciclo de vida do emitter
        emitter.onCompletion(() -> {
            this.emitters.remove(token);
            this.lastActivityTime.remove(token);
            log.info("SSE completado para cliente com token: {}", token);
        });

        emitter.onTimeout(() -> {
            emitter.complete();
            this.emitters.remove(token);
            this.lastActivityTime.remove(token);
            log.info("SSE timeout para cliente com token: {}", token);
        });

        emitter.onError((Throwable e) -> {
            this.emitters.remove(token);
            this.lastActivityTime.remove(token);
            log.error("Erro no SSE para cliente com token {}: {}", token, e.getMessage());
        });

        // Armazena o emitter e o timestamp de atividade
        this.emitters.put(token, emitter);
        this.lastActivityTime.put(token, System.currentTimeMillis());

        // Envia um evento inicial para confirmar a conexão
        executor.execute(() -> {
            try {
                // Gera um ID único para a conexão
                String connectionId = UUID.randomUUID().toString();
                
                // Convertendo Map para String JSON antes de enviar
                String connectionData = objectMapper.writeValueAsString(Map.of(
                        "message", "Conexão SSE estabelecida com sucesso",
                        "token", token,
                        "connectionId", connectionId,
                        "timestamp", System.currentTimeMillis()
                ));

                emitter.send(SseEmitter.event()
                        .id(connectionId)
                        .name("connect")
                        .data(connectionData, MediaType.APPLICATION_JSON));

                // Enviamos uma lista de ferramentas disponíveis com parâmetros
                Map<String, Object> toolsInfo = new HashMap<>();
                toolsInfo.put("message", "Ferramentas disponíveis");

                Map<String, Object> tools = new HashMap<>();

                // Ferramenta: check_availability
                Map<String, Object> checkAvailability = new HashMap<>();
                checkAvailability.put("description", "Verifica disponibilidade em uma data e horário específicos");
                checkAvailability.put("parameters", Map.of(
                        "appointmentDate", "string (data, formato: yyyy-MM-dd)",
                        "appointmentTime", "string (horário, formato: HH:mm)"
                ));

                // Ferramenta: create_appointment
                Map<String, Object> createAppointment = new HashMap<>();
                createAppointment.put("description", "Cria um novo agendamento");
                createAppointment.put("parameters", Map.of(
                        "appointmentDate", "string (data do agendamento, formato: yyyy-MM-dd)",
                        "appointmentTime", "string (horário do agendamento, formato: HH:mm)",
                        "name", "string (nome do agendamento)",
                        "summary", "string (descrição/resumo)"
                ));

                // Ferramenta: get_appointment_details
                Map<String, Object> getAppointmentDetails = new HashMap<>();
                getAppointmentDetails.put("description", "Obtém detalhes de um agendamento");
                getAppointmentDetails.put("parameters", Map.of(
                        "id", "string (ID do agendamento)"
                ));

                // Ferramenta: cancel_appointment
                Map<String, Object> cancelAppointment = new HashMap<>();
                cancelAppointment.put("description", "Cancela um agendamento existente");
                cancelAppointment.put("parameters", Map.of(
                        "id", "string (ID do agendamento)"
                ));

                // Ferramenta: reschedule_appointment
                Map<String, Object> rescheduleAppointment = new HashMap<>();
                rescheduleAppointment.put("description", "Reagenda um agendamento existente com nova data, horário, nome e resumo.");
                rescheduleAppointment.put("parameters", Map.of(
                        "id", "string (ID do agendamento)",
                        "appointmentDate", "string (data do agendamento, formato: yyyy-MM-dd)",
                        "appointmentTime", "string (horário do agendamento, formato: HH:mm)",
                        "name", "string (nome do agendamento)",
                        "summary", "string (descrição/resumo)"
                ));

                // Adiciona todas as ferramentas
                tools.put("check_availability", checkAvailability);
                tools.put("create_appointment", createAppointment);
                tools.put("get_appointment_details", getAppointmentDetails);
                tools.put("cancel_appointment", cancelAppointment);
                tools.put("reschedule_Appointment", rescheduleAppointment);

                toolsInfo.put("tools", tools);

                // Convertendo para String JSON
                String toolsJson = objectMapper.writeValueAsString(toolsInfo);

                emitter.send(SseEmitter.event()
                        .id(UUID.randomUUID().toString())
                        .name("tools")
                        .data(toolsJson, MediaType.APPLICATION_JSON));

            } catch (JsonProcessingException e) {
                log.error("Erro ao serializar dados para JSON: {}", e.getMessage());
                try {
                    emitter.send(SseEmitter.event()
                            .id(UUID.randomUUID().toString())
                            .name("error")
                            .data("Erro interno ao processar dados: " + e.getMessage()));
                } catch (IOException ioe) {
                    log.error("Erro ao enviar erro para cliente com token {}: {}", token, ioe.getMessage());
                    emitter.completeWithError(ioe);
                }
            } catch (IOException e) {
                log.error("Erro ao enviar evento para cliente com token {}: {}", token, e.getMessage());
                emitter.completeWithError(e);
            }
        });

        return emitter;
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
     * @return Número de clientes SSE atualmente conectados
     */
    public int getConnectedClientsCount() {
        return this.emitters.size();
    }
    
    /**
     * Obtém token para um IP específico
     */
    public String getTokenForIp(String ip) {
        return ipToTokenMap.get(ip);
    }
    
    /**
     * Verifica se existe um cliente com o token especificado
     */
    public boolean hasClient(String token) {
        return emitters.containsKey(token);
    }
    
    /**
     * Verifica e fecha conexões inativas
     * @param timeoutThreshold O timestamp limite para considerar uma conexão inativa
     */
    public void checkAndCloseInactiveConnections(long timeoutThreshold) {
        if (emitters.isEmpty()) {
            return;
        }
        
        // Copia das chaves para evitar ConcurrentModificationException
        new HashMap<>(lastActivityTime).forEach((token, lastActivity) -> {
            if (lastActivity < timeoutThreshold) {
                log.info("Fechando conexão inativa para cliente {}", token);
                SseEmitter emitter = emitters.get(token);
                if (emitter != null) {
                    emitter.complete();
                    emitters.remove(token);
                    lastActivityTime.remove(token);
                }
            }
        });
    }
    
    /**
     * Envia heartbeat para todos os clientes conectados
     */
    public void sendHeartbeatToAllClients() {
        if (emitters.isEmpty()) {
            return;
        }
        
        emitters.forEach((token, emitter) -> {
            try {
                // Atualiza o timestamp de atividade
                this.lastActivityTime.put(token, System.currentTimeMillis());
                
                // Envia o evento heartbeat
                emitter.send(SseEmitter.event()
                        .id(UUID.randomUUID().toString())
                        .name("heartbeat")
                        .data(Map.of(
                            "timestamp", System.currentTimeMillis(),
                            "message", "heartbeat"
                        ), MediaType.APPLICATION_JSON));
                
            } catch (IOException e) {
                log.warn("Erro ao enviar heartbeat para cliente {}: {}", token, e.getMessage());
                // Não removemos o emitter aqui, deixamos para o mecanismo de timeout
            }
        });
    }
}
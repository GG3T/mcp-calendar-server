package chatgm.com.mcp_server.controller;

import chatgm.com.mcp_server.dto.CalendariosResponse;
import chatgm.com.mcp_server.exception.TokenInvalidoException;
import chatgm.com.mcp_server.exception.UsuarioNaoAutorizadoException;
import chatgm.com.mcp_server.exception.UsuarioNaoEncontradoException;
import chatgm.com.mcp_server.model.Usuario;
import chatgm.com.mcp_server.repository.UsuarioRepository;
import chatgm.com.mcp_server.service.CalendarioService;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SseController {

    private final CalendarioService calendarioService;
    private final UsuarioRepository usuarioRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    
    // Armazena o email do usuário ativo na thread atual
    private final ThreadLocal<String> currentUserEmail = new ThreadLocal<>();
    
    /**
     * Obtém o email do usuário atual para ser usado em chamadas MCP
     * @return email do usuário atualmente autenticado
     */
    public String getCurrentUserEmail() {
        // Primeiro verifica se há um email já definido no ThreadLocal
        String email = currentUserEmail.get();
        if (email != null && !email.isEmpty()) {
            return email;
        }
        
        // Tenta obter do atributo de requisição
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                // Busca o email do cabeçalho
                email = request.getHeader("Authorization");
                if (email == null || email.isEmpty()) {
                    // Busca o email do parâmetro
                    email = request.getParameter("email");
                }
                
                // Verificar se o email está disponível
                if (email != null && !email.isEmpty()) {
                    // Armazena no ThreadLocal para futuras chamadas
                    currentUserEmail.set(email);
                    return email;
                }
            }
            
            // Se chegou aqui, não conseguiu obter o email
            log.warn("Não foi possível determinar o email do usuário atual");
            return null;
        } catch (Exception e) {
            log.error("Erro ao obter o email do usuário atual: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Define o email do usuário atual para o ThreadLocal
     * @param email o email a ser definido
     */
    public void setCurrentUserEmail(String email) {
        if (email != null && !email.isEmpty()) {
            currentUserEmail.set(email);
            log.debug("Email definido para thread atual: {}", email);
        } else {
            currentUserEmail.remove();
            log.debug("Email removido da thread atual");
        }
    }
    
    /**
     * Verifica se o usuário está autorizado baseado no email
     * @param email o email do usuário para verificar
     * @return o objeto Usuario se autorizado
     * @throws UsuarioNaoAutorizadoException se o usuário não estiver autorizado
     */
    private Usuario verificarAutorizacao(String email) {
        if (email == null || email.isEmpty()) {
            log.error("Email não pode ser nulo ou vazio");
            throw new UsuarioNaoAutorizadoException("Email não fornecido");
        }
        
        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(email);
        
        if (usuarioOpt.isEmpty()) {
            log.warn("Tentativa de acesso não autorizado com email: {}", email);
            throw new UsuarioNaoAutorizadoException(email);
        }
        
        log.info("Usuário autorizado: {}", email);
        return usuarioOpt.get();
    }

    /**
     * Endpoint SSE para estabelecer uma conexão persistente
     * @param authHeader o email do usuário no cabeçalho Authorization (opcional)
     * @param emailParam o email do usuário como parâmetro da URL (opcional)
     * @return SseEmitter para a conexão persistente
     */
    @GetMapping(path = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "email", required = false) String emailParam) {
        
        // Determina o email a ser usado para autenticação
        final String authenticatedEmail;
        
        // Tenta obter o email do header ou do parâmetro da URL
        if (authHeader != null && !authHeader.isEmpty()) {
            authenticatedEmail = authHeader;
            log.debug("Email obtido do header Authorization: {}", authenticatedEmail);
        } else if (emailParam != null && !emailParam.isEmpty()) {
            authenticatedEmail = emailParam;
            log.debug("Email obtido do parâmetro de URL: {}", authenticatedEmail);
        } else {
            log.error("Tentativa de conexão sem fornecer o email");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, 
                "Email não fornecido. Use o header Authorization ou o parâmetro email na URL");
        }
        
        // Define o email para a thread atual
        setCurrentUserEmail(authenticatedEmail);
        
        try {
            verificarAutorizacao(authenticatedEmail);
        } catch (UsuarioNaoAutorizadoException e) {
            log.error("Erro de autorização: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
        
        log.info("Nova conexão SSE recebida com email: {}", authenticatedEmail);
        
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        
        // Configuração para gerenciar o ciclo de vida do emitter
        emitter.onCompletion(() -> {
            this.emitters.remove(authenticatedEmail);
            log.info("SSE completado para cliente: {}", authenticatedEmail);
        });
        
        emitter.onTimeout(() -> {
            emitter.complete();
            this.emitters.remove(authenticatedEmail);
            log.info("SSE timeout para cliente: {}", authenticatedEmail);
        });
        
        emitter.onError(e -> {
            emitter.complete();
            this.emitters.remove(authenticatedEmail);
            log.error("Erro no SSE para cliente {}: {}", authenticatedEmail, e.getMessage());
        });
        
        // Armazena o emitter
        this.emitters.put(authenticatedEmail, emitter);
        
        // Envia um evento inicial para confirmar a conexão
        executor.execute(() -> {
            try {
                // Convertendo Map para String JSON antes de enviar
                String connectionData = objectMapper.writeValueAsString(Map.of(
                        "message", "Conexão SSE estabelecida com sucesso",
                        "email", authenticatedEmail,
                        "timestamp", System.currentTimeMillis()
                ));
                
                emitter.send(SseEmitter.event()
                        .name("connect")
                        .data(connectionData, MediaType.APPLICATION_JSON));
                
                // Tenta buscar os calendários do usuário
                try {
                    List<String> calendarios = calendarioService.getCalendariosByUserEmail(authenticatedEmail);
                    CalendariosResponse response = new CalendariosResponse(calendarios);
                    
                    // Convertendo para String JSON
                    String calendariosJson = objectMapper.writeValueAsString(response);
                    
                    emitter.send(SseEmitter.event()
                            .name("calendarios")
                            .data(calendariosJson, MediaType.APPLICATION_JSON));
                    
                    log.info("Calendários enviados via SSE para o cliente: {}", authenticatedEmail);
                } catch (UsuarioNaoEncontradoException | TokenInvalidoException e) {
                    // Convertendo para String JSON
                    String errorData = objectMapper.writeValueAsString(Map.of(
                        "message", e.getMessage(),
                        "type", e.getClass().getSimpleName()
                    ));
                    
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(errorData, MediaType.APPLICATION_JSON));
                    log.error("Erro ao buscar calendários para cliente {}: {}", authenticatedEmail, e.getMessage());
                }
                
            } catch (JsonProcessingException e) {
                log.error("Erro ao serializar dados para JSON: {}", e.getMessage());
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("Erro interno ao processar dados: " + e.getMessage()));
                } catch (IOException ioe) {
                    log.error("Erro ao enviar erro para cliente {}: {}", authenticatedEmail, ioe.getMessage());
                    emitter.completeWithError(ioe);
                }
            } catch (IOException e) {
                log.error("Erro ao enviar evento para cliente {}: {}", authenticatedEmail, e.getMessage());
                emitter.completeWithError(e);
            }
        });
        
        return emitter;
    }
    
    /**
     * Método auxiliar para enviar mensagens para um cliente específico
     */
    public void sendMessageToClient(String email, Object data, String eventName) {
        SseEmitter emitter = this.emitters.get(email);
        if (emitter != null) {
            executor.execute(() -> {
                try {
                    // Convertendo para String JSON
                    String jsonData = objectMapper.writeValueAsString(data);
                    
                    emitter.send(SseEmitter.event()
                            .name(eventName)
                            .data(jsonData, MediaType.APPLICATION_JSON));
                } catch (JsonProcessingException e) {
                    log.error("Erro ao serializar dados para JSON: {}", e.getMessage());
                } catch (IOException e) {
                    log.error("Erro ao enviar mensagem para cliente {}: {}", email, e.getMessage());
                    emitter.completeWithError(e);
                }
            });
        }
    }
    
    /**
     * @return Número de clientes SSE atualmente conectados
     */
    public int getConnectedClientsCount() {
        return this.emitters.size();
    }
}

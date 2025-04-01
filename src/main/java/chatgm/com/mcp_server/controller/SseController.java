package chatgm.com.mcp_server.controller;

import chatgm.com.mcp_server.dto.CalendariosResponse;
import chatgm.com.mcp_server.exception.TokenInvalidoException;
import chatgm.com.mcp_server.exception.UsuarioNaoEncontradoException;
import chatgm.com.mcp_server.service.CalendarioService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SseController {

    private final CalendarioService calendarioService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * Endpoint SSE para estabelecer uma conexão persistente
     * @param email o email do usuário, fornecido no cabeçalho
     * @return SseEmitter para a conexão persistente
     */
    @GetMapping(path = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(@RequestHeader(value = "email", required = true) String email) {
        log.info("Nova conexão SSE recebida com email: {}", email);
        
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        
        // Configuração para gerenciar o ciclo de vida do emitter
        emitter.onCompletion(() -> {
            this.emitters.remove(email);
            log.info("SSE completado para cliente: {}", email);
        });
        
        emitter.onTimeout(() -> {
            emitter.complete();
            this.emitters.remove(email);
            log.info("SSE timeout para cliente: {}", email);
        });
        
        emitter.onError(e -> {
            emitter.complete();
            this.emitters.remove(email);
            log.error("Erro no SSE para cliente {}: {}", email, e.getMessage());
        });
        
        // Armazena o emitter
        this.emitters.put(email, emitter);
        
        // Envia um evento inicial para confirmar a conexão
        executor.execute(() -> {
            try {
                // Convertendo Map para String JSON antes de enviar
                String connectionData = objectMapper.writeValueAsString(Map.of(
                        "message", "Conexão SSE estabelecida com sucesso",
                        "email", email,
                        "timestamp", System.currentTimeMillis()
                ));
                
                emitter.send(SseEmitter.event()
                        .name("connect")
                        .data(connectionData, MediaType.APPLICATION_JSON));
                
                // Tenta buscar os calendários do usuário
                try {
                    List<String> calendarios = calendarioService.getCalendariosByUserEmail(email);
                    CalendariosResponse response = new CalendariosResponse(calendarios);
                    
                    // Convertendo para String JSON
                    String calendariosJson = objectMapper.writeValueAsString(response);
                    
                    emitter.send(SseEmitter.event()
                            .name("calendarios")
                            .data(calendariosJson, MediaType.APPLICATION_JSON));
                    
                    log.info("Calendários enviados via SSE para o cliente: {}", email);
                } catch (UsuarioNaoEncontradoException | TokenInvalidoException e) {
                    // Convertendo para String JSON
                    String errorData = objectMapper.writeValueAsString(Map.of(
                        "message", e.getMessage(),
                        "type", e.getClass().getSimpleName()
                    ));
                    
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(errorData, MediaType.APPLICATION_JSON));
                    log.error("Erro ao buscar calendários para cliente {}: {}", email, e.getMessage());
                }
                
            } catch (JsonProcessingException e) {
                log.error("Erro ao serializar dados para JSON: {}", e.getMessage());
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("Erro interno ao processar dados: " + e.getMessage()));
                } catch (IOException ioe) {
                    log.error("Erro ao enviar erro para cliente {}: {}", email, ioe.getMessage());
                    emitter.completeWithError(ioe);
                }
            } catch (IOException e) {
                log.error("Erro ao enviar evento para cliente {}: {}", e.getMessage());
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

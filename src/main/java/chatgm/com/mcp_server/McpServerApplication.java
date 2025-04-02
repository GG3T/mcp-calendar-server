package chatgm.com.mcp_server;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import chatgm.com.mcp_server.controller.SseController;
import chatgm.com.mcp_server.dto.CalendariosResponse;
import chatgm.com.mcp_server.exception.TokenInvalidoException;
import chatgm.com.mcp_server.exception.UsuarioNaoEncontradoException;
import chatgm.com.mcp_server.service.CalendarioService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootApplication
public class McpServerApplication {
    private static final Logger logger = LoggerFactory.getLogger(McpServerApplication.class);

    @Value("${server.address:localhost}")
    private String serverAddress;

    @Value("${server.port:8080}")
    private int serverPort;
    
    @Value("${spring.ai.mcp.server.external-url:}")
    private String externalUrl;

    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }

    // CommandLineRunner para logar informações importantes no startup
    @Bean
    public CommandLineRunner logStartupInfo() {
        return args -> {
            logger.info("MCP Calendar Server iniciado");
            logger.info("Servidor rodando em http://{}:{}", serverAddress, serverPort);
            
            String baseUrl = externalUrl.isEmpty() 
                ? "http://" + serverAddress + ":" + serverPort 
                : externalUrl;
                
            logger.info("URL externa configurada: {}", baseUrl);
            logger.info("Endpoint SSE disponível em: {}/sse", baseUrl);
            logger.info("Opções de autenticação SSE:");
            logger.info("  - Via header: Authorization: seu-email@exemplo.com");
            logger.info("  - Via URL: {}/sse?email=seu-email@exemplo.com", baseUrl);
            logger.info("Endpoint REST disponível em: {}/calendarios", baseUrl);
            
            // Remova a referência à página de teste que foi excluída
            // logger.info("Página de teste disponível em {}/static/sse-test.html", baseUrl);
        };
    }

    // Registro da ferramenta (tool) - MCP
    @Bean
    public ToolCallbackProvider toolCallbackProvider(CalendarTool calendarTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(calendarTool)
                .build();
    }

    @Bean
    public List<McpServerFeatures.SyncResourceRegistration> resourceRegistrations() {
        McpSchema.Resource systemResource = new McpSchema.Resource(
                "custom://serverInfo",
                "serverInfo",
                "Informações do Servidor",
                "application/json",
                null
        );

        // Registra o recurso com um handler que retorna informações do sistema em JSON.
        McpServerFeatures.SyncResourceRegistration registration = new McpServerFeatures.SyncResourceRegistration(
                systemResource,
                (request) -> {
                    String baseUrl = externalUrl.isEmpty() 
                        ? "http://" + serverAddress + ":" + serverPort 
                        : externalUrl;
                        
                    Map<String, Object> serverInfo = new HashMap<>();
                    serverInfo.put("name", "MCP Calendar Server");
                    serverInfo.put("version", "0.2.0");
                    serverInfo.put("javaVersion", System.getProperty("java.version"));
                    serverInfo.put("baseUrl", baseUrl);
                    serverInfo.put("endpoints", Map.of(
                        "sse", baseUrl + "/sse",
                        "sseWithEmail", baseUrl + "/sse?email=seu-email@exemplo.com",
                        "calendarios", baseUrl + "/calendarios"
                    ));
                    
                    try {
                        String json = new ObjectMapper().writeValueAsString(serverInfo);
                        return new McpSchema.ReadResourceResult(
                                List.of(new McpSchema.TextResourceContents("custom://serverInfo", "application/json", json))
                        );
                    } catch (Exception e) {
                        throw new RuntimeException("Erro ao obter informações do servidor", e);
                    }
                }
        );
        return List.of(registration);
    }

    @Service
    static class CalendarTool {
        @Autowired
        private CalendarioService calendarioService;
        
        @Autowired
        private SseController sseController;
        
        @Tool(description = "Busca os calendários disponíveis para o usuário autenticado. Retorna a lista de calendários do Google associados à conta do usuário que está atualmente conectado via SSE.")
        public Map<String, Object> BuscarCalendarios() {
            try {
                // Uma alternativa é obter o usuário atualmente autenticado diretamente via SseController
                // Em vez de depender da classe McpServerRequestContext que não está disponível
                String email = sseController.getCurrentUserEmail();
                
                if (email == null || email.isEmpty()) {
                    return Map.of("success", false, "error", "Não foi possível identificar o usuário. Verifique a autenticação.");
                }
                
                List<String> calendarios = calendarioService.getCalendariosByUserEmail(email);
                return Map.of(
                    "success", true, 
                    "email", email,
                    "calendarios", calendarios
                );
            } catch (UsuarioNaoEncontradoException e) {
                return Map.of("success", false, "error", "Usuário não encontrado: " + e.getMessage());
            } catch (TokenInvalidoException e) {
                return Map.of("success", false, "error", "Token inválido ou expirado: " + e.getMessage());
            } catch (Exception e) {
                return Map.of("success", false, "error", "Erro ao buscar calendários: " + e.getMessage());
            }
        }
    }
}

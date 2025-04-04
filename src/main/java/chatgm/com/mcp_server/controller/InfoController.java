package chatgm.com.mcp_server.controller;

import chatgm.com.mcp_server.config.SseConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Controlador para endpoint de informações da aplicação
 */
@RestController
@RequestMapping("/api/info")
public class InfoController {

    @Value("${spring.ai.mcp.server.name:mcp-calendar-server}")
    private String serverName;

    @Value("${spring.ai.mcp.server.version:0.4.0}")
    private String serverVersion;

    @Value("${spring.ai.mcp.server.external-url:http://localhost:3500}")
    private String externalUrl;

    @Value("${server.port:3500}")
    private int serverPort;

    @Autowired
    private SseConfig sseConfig;

    @Autowired
    private SseController sseController;

    @Autowired(required = false)
    private Optional<BuildProperties> buildProperties;

    private final long startupTime = System.currentTimeMillis();

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAppInfo() {
        Map<String, Object> info = new HashMap<>();
        
        // Informações básicas da aplicação
        info.put("name", serverName);
        info.put("version", serverVersion);
        info.put("description", "MCP Calendar Server for Google Calendar Integration");
        
        // Informações de build se disponíveis
        buildProperties.ifPresent(props -> {
            Map<String, Object> buildInfo = new HashMap<>();
            buildInfo.put("version", props.getVersion());
            buildInfo.put("time", props.getTime());
            buildInfo.put("artifact", props.getArtifact());
            buildInfo.put("group", props.getGroup());
            info.put("build", buildInfo);
        });
        
        // Informações de runtime
        Map<String, Object> runtime = new HashMap<>();
        runtime.put("javaVersion", System.getProperty("java.version"));
        runtime.put("javaVendor", System.getProperty("java.vendor"));
        runtime.put("osName", System.getProperty("os.name"));
        
        // Uptime
        long uptime = System.currentTimeMillis() - startupTime;
        runtime.put("startupTime", formatTimestamp(startupTime));
        runtime.put("uptime", formatDuration(uptime));
        
        info.put("runtime", runtime);
        
        // Configuração de SSE
        Map<String, Object> sseInfo = new HashMap<>();
        sseInfo.put("activeConnections", sseController.getConnectedClientsCount());
        sseInfo.put("timeoutMillis", sseConfig.getTimeoutMillis());
        sseInfo.put("heartbeatEnabled", sseConfig.isHeartbeatEnabled());
        sseInfo.put("heartbeatIntervalMillis", sseConfig.getHeartbeatIntervalMillis());
        
        info.put("sse", sseInfo);
        
        // Informações de endpoint
        Map<String, Object> endpoints = new HashMap<>();
        endpoints.put("base", externalUrl);
        endpoints.put("sse", externalUrl + "/sse");
        endpoints.put("health", externalUrl + "/actuator/health");
        endpoints.put("ping", externalUrl + "/ping");
        
        info.put("endpoints", endpoints);
        
        return ResponseEntity.ok(info);
    }
    
    /**
     * Formata um timestamp em uma string legível
     */
    private String formatTimestamp(long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp), 
                ZoneId.systemDefault());
        return dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
    
    /**
     * Formata uma duração em milissegundos em uma string legível
     */
    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        return String.format("%d dias, %d horas, %d minutos, %d segundos",
                days, hours % 24, minutes % 60, seconds % 60);
    }
}

package chatgm.com.mcp_server.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Controlador para endpoint de ping
 * Este endpoint é usado para verificações de saúde e não gera logs
 */
@RestController
public class PingController {

    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of(
                "status", "up",
                "timestamp", Instant.now().toEpochMilli()
        ));
    }
}

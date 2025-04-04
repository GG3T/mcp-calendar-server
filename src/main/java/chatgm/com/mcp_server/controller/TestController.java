package chatgm.com.mcp_server.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controlador para testes e endpoints auxiliares.
 * Não usado em produção.
 */
@RestController
public class TestController {

    /**
     * Endpoint de teste
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> test() {
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "message", "Test endpoint is working",
            "timestamp", System.currentTimeMillis()
        ));
    }
}

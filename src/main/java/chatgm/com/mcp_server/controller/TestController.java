package chatgm.com.mcp_server.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class TestController {

    /**
     * Endpoint simples para verificar se o servidor está funcionando
     */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "message", "Server is running",
            "timestamp", System.currentTimeMillis()
        ));
    }
}

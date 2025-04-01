package chatgm.com.mcp_server.controller;

import chatgm.com.mcp_server.dto.CalendariosResponse;
import chatgm.com.mcp_server.service.CalendarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/calendarios")
@RequiredArgsConstructor
@Slf4j
public class CalendarioController {

    private final CalendarioService calendarioService;

    /**
     * Endpoint para obter a lista de calendários do usuário
     * @param email o email do usuário, fornecido no cabeçalho
     * @return resposta com a lista de IDs dos calendários
     */
    @GetMapping
    public ResponseEntity<CalendariosResponse> getCalendarios(
            @RequestHeader(name = "email", required = true) String email) {
        
        log.info("Recebida requisição para buscar calendários do usuário: {}", email);
        
        List<String> calendarios = calendarioService.getCalendariosByUserEmail(email);
        
        log.info("Encontrados {} calendários para o usuário: {}", calendarios.size(), email);
        
        return ResponseEntity.ok(new CalendariosResponse(calendarios));
    }
}

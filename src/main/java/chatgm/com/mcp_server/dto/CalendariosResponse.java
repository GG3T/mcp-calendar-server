package chatgm.com.mcp_server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO para a resposta da API com a lista de calendários
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CalendariosResponse {
    private List<String> calendarios;
}

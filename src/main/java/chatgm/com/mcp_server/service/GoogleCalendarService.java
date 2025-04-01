package chatgm.com.mcp_server.service;

import chatgm.com.mcp_server.dto.CalendarListResponse;
import chatgm.com.mcp_server.exception.TokenInvalidoException;
import chatgm.com.mcp_server.model.TokenOauth;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class GoogleCalendarService {

    private final WebClient webClient;
    private static final String CALENDAR_LIST_URL = "https://www.googleapis.com/calendar/v3/users/me/calendarList";

    public GoogleCalendarService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("https://www.googleapis.com")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Busca a lista de calendários do usuário através da API do Google Calendar
     * @param tokenOauth o token OAuth do usuário
     * @return lista de IDs dos calendários
     * @throws TokenInvalidoException caso o token seja inválido ou ocorra um erro de autorização
     */
    public List<String> getCalendarList(TokenOauth tokenOauth) {
        if (tokenOauth.isExpirado()) {
            throw new TokenInvalidoException("O token OAuth está expirado e não pode ser utilizado.");
        }

        try {
            CalendarListResponse response = webClient.get()
                    .uri(CALENDAR_LIST_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenOauth.getAccessToken())
                    .retrieve()
                    .bodyToMono(CalendarListResponse.class)
                    .onErrorResume(WebClientResponseException.class, ex -> {
                        if (ex.getStatusCode().is4xxClientError()) {
                            log.error("Erro de autorização ao acessar a API do Google Calendar: {}", ex.getMessage());
                            throw new TokenInvalidoException("Erro de autorização ao acessar a API do Google Calendar", ex);
                        }
                        log.error("Erro ao acessar a API do Google Calendar: {}", ex.getMessage());
                        return Mono.error(ex);
                    })
                    .block();

            if (response == null || response.getItems() == null) {
                return List.of();
            }

            return response.getItems().stream()
                    .map(CalendarListResponse.CalendarItem::getId)
                    .collect(Collectors.toList());
        } catch (TokenInvalidoException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao buscar lista de calendários: {}", e.getMessage());
            throw new RuntimeException("Erro ao buscar lista de calendários", e);
        }
    }
}

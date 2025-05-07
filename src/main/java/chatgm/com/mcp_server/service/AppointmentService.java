package chatgm.com.mcp_server.service;

import chatgm.com.mcp_server.dto.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;


import java.net.URI;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {

    private final WebClient webClient;

    @Value("${appointment.api.base-url:https://service.mcpgod.com.br/api}")
    private String appointmentApiBaseUrl;

    /**
     * Verifica a disponibilidade para um horário específico
     * @param token Token de autenticação
     * @param request Dados da requisição contendo a data/hora para verificar
     * @return Resposta indicando se o horário está disponível
     */
    public AvailabilityResponse checkAvailability(String token, AvailabilityRequest request) {
        log.info("Verificando disponibilidade para a data: {} (Horário de São Paulo/Brazil)", request.getAppointmentDate());
        
        // Ajuste para utilizar o fuso horário de São Paulo/Brazil
        ZoneId brazilZone = ZoneId.of("America/Sao_Paulo");
        log.info("Utilizando fuso horário: {}", brazilZone);

        // Utilize HTTPS para evitar redirecionamento
        String baseUrl = "https://service.mcpgod.com.br";
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/api/appointments/availability")
                .queryParam("token", token)
                .build()
                .toUri();
        log.info("Request URI: {}", uri.toString());

        try {
            return webClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchangeToMono(response -> {
                        log.info("Response Status Code: {}", response.statusCode());
                        
                        if (response.statusCode().isError()) {
                            return response.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        log.info("Error Response Body: {}", body);
                                        AvailabilityResponse errorResponse = new AvailabilityResponse();
                                        errorResponse.setAvailable(false);
                                        errorResponse.setMessage("Erro ao verificar disponibilidade: " + body);
                                        return Mono.just(errorResponse);
                                    });
                        }
                        
                        return response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.info("Response Body: {}", body);
                                    try {
                                        ObjectMapper mapper = new ObjectMapper();
                                        mapper.registerModule(new JavaTimeModule());
                                        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                                        
                                        AvailabilityResponse availabilityResponse = mapper.readValue(body, AvailabilityResponse.class);
                                        return Mono.just(availabilityResponse);
                                    } catch (JsonProcessingException e) {
                                        log.error("Erro ao processar JSON: {}", e.getMessage());
                                        AvailabilityResponse errorResponse = new AvailabilityResponse();
                                        errorResponse.setAvailable(false);
                                        errorResponse.setMessage("Erro ao processar resposta: " + e.getMessage());
                                        return Mono.just(errorResponse);
                                    }
                                });
                    })
                    .onErrorResume(WebClientResponseException.class, ex -> {
                        log.error("Erro ao verificar disponibilidade: {} - {}", ex.getStatusCode(), ex.getMessage());
                        AvailabilityResponse errorResponse = new AvailabilityResponse();
                        errorResponse.setAvailable(false);
                        errorResponse.setMessage("Erro ao verificar disponibilidade: " + ex.getMessage());
                        return Mono.just(errorResponse);
                    })
                    .block();
        } catch (Exception e) {
            log.error("Erro ao verificar disponibilidade: {}", e.getMessage());
            AvailabilityResponse errorResponse = new AvailabilityResponse();
            errorResponse.setAvailable(false);
            errorResponse.setMessage("Erro ao verificar disponibilidade: " + e.getMessage());
            return errorResponse;
        }
    }

    /**
     * Reagenda um agendamento existente.
     * @param token Token de autenticação.
     * @param id ID do agendamento a ser reagendado.
     * @param request Dados do reagendamento.
     * @return Objeto do agendamento reagendado.
     */

    public AppointmentDto rescheduleAppointment(String token, String id, AppointmentRequest request) {
        log.info("Reagendando agendamento {} para a nova data: {} (Horário de São Paulo/Brazil)", id, request.getAppointmentDate());
        
        // Ajuste para utilizar o fuso horário de São Paulo/Brazil
        ZoneId brazilZone = ZoneId.of("America/Sao_Paulo");
        log.info("Utilizando fuso horário: {}", brazilZone);

        // Utilize HTTPS para evitar redirecionamento
        String baseUrl = "https://service.mcpgod.com.br";
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/api/appointments/{id}")
                .queryParam("token", token)
                .buildAndExpand(id)
                .toUri();
        log.info("Request URI: {}", uri.toString());

        try {
            return webClient.put()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchangeToMono(response -> {
                        log.info("Response Status Code: {}", response.statusCode());
                        
                        if (response.statusCode().isError()) {
                            return response.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        log.info("Error Response Body: {}", body);
                                        return Mono.error(new RuntimeException("Erro ao reagendar agendamento: " + body));
                                    });
                        }
                        
                        return response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.info("Response Body: {}", body);
                                    try {
                                        ObjectMapper mapper = new ObjectMapper();
                                        mapper.registerModule(new JavaTimeModule());
                                        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                                        
                                        AppointmentDto appointment = mapper.readValue(body, AppointmentDto.class);
                                        return Mono.just(appointment);
                                    } catch (JsonProcessingException e) {
                                        log.error("Erro ao processar JSON: {}", e.getMessage());
                                        return Mono.error(new RuntimeException("Erro ao processar resposta: " + e.getMessage()));
                                    }
                                });
                    })
                    .onErrorResume(WebClientResponseException.class, ex -> {
                        log.error("Erro ao reagendar agendamento: {} - {}", ex.getStatusCode(), ex.getMessage());
                        throw new RuntimeException("Erro ao reagendar agendamento: " + ex.getMessage());
                    })
                    .block();
        } catch (Exception e) {
            log.error("Erro ao reagendar agendamento: {}", e.getMessage());
            throw new RuntimeException("Erro ao reagendar agendamento: " + e.getMessage());
        }
    }

    /**
     * Cria um novo agendamento
     * @param token Token de autenticação
     * @param request Dados do agendamento a ser criado
     * @return Objeto do agendamento criado
     */
    public AppointmentDto createAppointment(String token, AppointmentRequest request) {
        log.info("Criando agendamento para a data: {} (Horário de São Paulo/Brazil)", request.getAppointmentDate());
        
        // Ajuste para utilizar o fuso horário de São Paulo/Brazil
        ZoneId brazilZone = ZoneId.of("America/Sao_Paulo");
        log.info("Utilizando fuso horário: {}", brazilZone);

        // Utilize HTTPS para evitar redirecionamento
        String baseUrl = "https://service.mcpgod.com.br";
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/api/appointments")
                .queryParam("token", token)
                .build()
                .toUri();
        log.info("Request URI: {}", uri.toString());

        try {
            return webClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchangeToMono(response -> {
                        log.info("Response Status Code: {}", response.statusCode());
                        
                        if (response.statusCode().isError()) {
                            return response.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        log.info("Error Response Body: {}", body);
                                        return Mono.error(new RuntimeException("Erro ao criar agendamento: " + body));
                                    });
                        }
                        
                        return response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.info("Response Body: {}", body);
                                    try {
                                        ObjectMapper mapper = new ObjectMapper();
                                        mapper.registerModule(new JavaTimeModule());
                                        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                                        
                                        AppointmentDto appointment = mapper.readValue(body, AppointmentDto.class);
                                        return Mono.just(appointment);
                                    } catch (JsonProcessingException e) {
                                        log.error("Erro ao processar JSON: {}", e.getMessage());
                                        return Mono.error(new RuntimeException("Erro ao processar resposta: " + e.getMessage()));
                                    }
                                });
                    })
                    .onErrorResume(WebClientResponseException.class, ex -> {
                        log.error("Erro ao criar agendamento: {} - {}", ex.getStatusCode(), ex.getMessage());
                        throw new RuntimeException("Erro ao criar agendamento: " + ex.getMessage());
                    })
                    .block();
        } catch (Exception e) {
            log.error("Erro ao criar agendamento: {}", e.getMessage());
            throw new RuntimeException("Erro ao criar agendamento: " + e.getMessage());
        }
    }

    /**
     * Obtém detalhes de um agendamento específico
     * @param token Token de autenticação
     * @param id Identificador do agendamento
     * @return Objeto com detalhes do agendamento
     */
    public AppointmentDto getAppointment(String token, String id) {
        log.info("Buscando agendamento com ID: {} (Horário de São Paulo/Brazil)", id);
        log.info("Token de autenticação: {}", token);
        
        // Ajuste para utilizar o fuso horário de São Paulo/Brazil
        ZoneId brazilZone = ZoneId.of("America/Sao_Paulo");
        log.info("Utilizando fuso horário: {}", brazilZone);

        // Utilize HTTPS para evitar redirecionamento
        String baseUrl = "https://service.mcpgod.com.br";
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/api/appointments/{id}")
                .queryParam("token", token)
                .buildAndExpand(id)
                .toUri();
        log.info("Request URI: {}", uri.toString());

        try {
            return webClient.get()
                    .uri(uri)
                    .exchangeToMono(response -> {
                        log.info("Response Status Code: {}", response.statusCode());
                        response.headers().asHttpHeaders().forEach((key, values) ->
                                values.forEach(value -> log.info("Response Header: {}: {}", key, value))
                        );
                        
                        if (response.statusCode().isError()) {
                            // Se receber código de erro, não tenta converter para AppointmentDto
                            return response.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        log.info("Response Body: {}", body);
                                        return Mono.error(new RuntimeException("Erro ao buscar agendamento: " + body));
                                    });
                        }
                        
                        return response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.info("Response Body: {}", body);
                                    try {
                                        ObjectMapper mapper = new ObjectMapper();
                                        // Registra o módulo para suportar LocalDateTime e outros tipos do Java 8
                                        mapper.registerModule(new JavaTimeModule());
                                        // Garante que a data não será serializada como timestamp
                                        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

                                        AppointmentDto appointment = mapper.readValue(body, AppointmentDto.class);
                                        return Mono.just(appointment);
                                    } catch (JsonProcessingException e) {
                                        return Mono.error(new RuntimeException("Erro ao processar resposta: " + e.getMessage()));
                                    }
                                });
                    })
                    .onErrorResume(WebClientResponseException.class, ex -> {
                        log.error("Erro ao buscar agendamento: {} - {}", ex.getStatusCode(), ex.getMessage());
                        return Mono.error(new RuntimeException("Erro ao buscar agendamento: " + ex.getMessage()));
                    })
                    .block();
        } catch (Exception e) {
            log.error("Erro ao buscar agendamento: {}", e.getMessage());
            throw new RuntimeException("Erro ao buscar agendamento: " + e.getMessage());
        }
    }



    /**
     * Verifica disponibilidade por faixa de horário
     * @param token Token de autenticação
     * @param request Dados da requisição contendo a data, hora de início, hora de fim, duração e intervalo
     * @return Resposta contendo os slots de tempo disponíveis
     */
    public AvailabilityRangeResponseDto checkAvailabilityRange(String token, AvailabilityRangeRequestDto request) {
        log.info("Verificando disponibilidade por range para a data: {}, hora início: {}, hora fim: {} (Horário de São Paulo/Brazil)", 
                request.getAppointmentDate(), request.getStartTime(), request.getEndTime());
                
        // Ajuste para utilizar o fuso horário de São Paulo/Brazil
        ZoneId brazilZone = ZoneId.of("America/Sao_Paulo");
        
        // Log para registrar que estamos usando o fuso horário de São Paulo
        log.info("Utilizando fuso horário: {}", brazilZone);

        // Utilize HTTPS para evitar redirecionamento
        String baseUrl = "https://service.mcpgod.com.br";
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/api/appointments/availability/range")
                .queryParam("token", token)
                .build()
                .toUri();
        log.info("Request URI: {}", uri.toString());

        try {
            return webClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchangeToMono(response -> {
                        log.info("Response Status Code: {}", response.statusCode());
                        
                        if (response.statusCode().isError()) {
                            return response.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        log.info("Error Response Body: {}", body);
                                        AvailabilityRangeResponseDto errorResponse = new AvailabilityRangeResponseDto();
                                        errorResponse.setMessage("Erro ao verificar disponibilidade por range: " + body);
                                        return Mono.just(errorResponse);
                                    });
                        }
                        
                        return response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.info("Response Body: {}", body);
                                    try {
                                        ObjectMapper mapper = new ObjectMapper();
                                        mapper.registerModule(new JavaTimeModule());
                                        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                                        
                                        AvailabilityRangeResponseDto availabilityResponse = mapper.readValue(body, AvailabilityRangeResponseDto.class);
                                        return Mono.just(availabilityResponse);
                                    } catch (JsonProcessingException e) {
                                        log.error("Erro ao processar JSON: {}", e.getMessage());
                                        AvailabilityRangeResponseDto errorResponse = new AvailabilityRangeResponseDto();
                                        errorResponse.setMessage("Erro ao processar resposta: " + e.getMessage());
                                        return Mono.just(errorResponse);
                                    }
                                });
                    })
                    .onErrorResume(WebClientResponseException.class, ex -> {
                        log.error("Erro ao verificar disponibilidade por range: {} - {}", ex.getStatusCode(), ex.getMessage());
                        AvailabilityRangeResponseDto errorResponse = new AvailabilityRangeResponseDto();
                        errorResponse.setMessage("Erro ao verificar disponibilidade por range: " + ex.getMessage());
                        return Mono.just(errorResponse);
                    })
                    .block();
        } catch (Exception e) {
            log.error("Erro ao verificar disponibilidade por range: {}", e.getMessage());
            AvailabilityRangeResponseDto errorResponse = new AvailabilityRangeResponseDto();
            errorResponse.setMessage("Erro ao verificar disponibilidade por range: " + e.getMessage());
            return errorResponse;
        }
    }

    /**
     * Cancela um agendamento existente
     * @param token Token de autenticação
     * @param id Identificador do agendamento
     * @return true se o agendamento foi cancelado com sucesso
     */
    public boolean cancelAppointment(String token, String id) {
        log.info("Cancelando agendamento com ID: {} (Horário de São Paulo/Brazil)", id);
        
        // Ajuste para utilizar o fuso horário de São Paulo/Brazil
        ZoneId brazilZone = ZoneId.of("America/Sao_Paulo");
        log.info("Utilizando fuso horário: {}", brazilZone);

        // Utilize HTTPS para evitar redirecionamento
        String baseUrl = "https://service.mcpgod.com.br";
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path("/api/appointments/{id}")
                .queryParam("token", token)
                .buildAndExpand(id)
                .toUri();
        log.info("Request URI: {}", uri.toString());

        try {
            webClient.delete()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchangeToMono(response -> {
                        log.info("Response Status Code: {}", response.statusCode());
                        
                        if (response.statusCode().isError()) {
                            return response.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        log.info("Error Response Body: {}", body);
                                        return Mono.error(new RuntimeException("Erro ao cancelar agendamento: " + body));
                                    });
                        }
                        
                        // Processa qualquer resposta como String primeiro para lidar com diferentes tipos de conteúdo
                        return response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    if (body != null && !body.isEmpty()) {
                                        log.info("Response Body: {}", body);
                                    }
                                    return Mono.just(true);
                                });
                    })
                    .onErrorResume(WebClientResponseException.class, ex -> {
                        log.error("Erro ao cancelar agendamento: {} - {}", ex.getStatusCode(), ex.getMessage());
                        throw new RuntimeException("Erro ao cancelar agendamento: " + ex.getMessage());
                    })
                    .block();
            return true;
        } catch (Exception e) {
            log.error("Erro ao cancelar agendamento: {}", e.getMessage());
            throw new RuntimeException("Erro ao cancelar agendamento: " + e.getMessage());
        }
    }
}
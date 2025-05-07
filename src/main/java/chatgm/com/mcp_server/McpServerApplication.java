package chatgm.com.mcp_server;

import chatgm.com.mcp_server.dto.*;
import chatgm.com.mcp_server.dto.AvailabilityRangeResponseDto.TimeSlot;
import chatgm.com.mcp_server.service.AppointmentService;
import chatgm.com.mcp_server.service.TokenService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
            logger.info("  - Via header: Authorization: seu-token");
            logger.info("  - Via header: token: seu-token");
            logger.info("  - Via URL: {}/sse?token=seu-token", baseUrl);

            // Ferramentas disponíveis
            logger.info("Ferramentas disponíveis:");
            logger.info("  - check_availability: Verifica disponibilidade para agendamento");
            logger.info("  - check_availability_range: Verifica disponibilidade por faixa de horário");
            logger.info("  - create_appointment: Cria um novo agendamento");
            logger.info("  - get_appointment_details: Obtém detalhes de um agendamento");
            logger.info("  - cancel_appointment: Cancela um agendamento existente");
        };
    }

    // Registro da ferramenta (tool) - MCP
    @Bean
    public ToolCallbackProvider toolCallbackProvider(AppointmentTool appointmentTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(appointmentTool)
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
                    serverInfo.put("version", "0.3.0");
                    serverInfo.put("javaVersion", System.getProperty("java.version"));
                    serverInfo.put("baseUrl", baseUrl);
                    serverInfo.put("endpoints", Map.of(
                            "sse", baseUrl + "/sse",
                            "sseWithToken", baseUrl + "/sse?token=seu-token-aqui"
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
    static class AppointmentTool {
        @Autowired
        private AppointmentService appointmentService;

        @Autowired
        private TokenService tokenService;

        /**
         * Método auxiliar para obter o token da requisição atual
         */
        private String resolveToken() {
            // Usa o serviço centralizado de tokens
            String token = tokenService.extractTokenFromCurrentRequest();
            
            if (token != null) {
                logger.debug("Token obtido com sucesso: {}", token);
                return token;
            }
            
            logger.warn("Não foi possível obter token por nenhum mecanismo");
            return null;
        }

        @Tool(description = "Verifica a disponibilidade para um agendamento em uma data e hora específica.")
        public Map<String, Object> CheckAvailability(String appointmentDate, String appointmentTime) {
            try {
                String token = resolveToken();
                if (token == null) {
                    return Map.of(
                            "success", false,
                            "error", "Não foi possível identificar o token. Verifique a autenticação."
                    );
                }

                // Converte as strings de data e horário para LocalDate e LocalTime no fuso horário de São Paulo (Brazil)
                DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
                
                // Usa ZoneId.of("America/Sao_Paulo") para garantir que estamos usando o horário de São Paulo
                ZoneId brazilZone = ZoneId.of("America/Sao_Paulo");
                LocalDate parsedDate = LocalDate.parse(appointmentDate, dateFormatter);
                LocalTime parsedTime = LocalTime.parse(appointmentTime, timeFormatter);

                // Cria o LocalDateTime combinando data e horário
                LocalDateTime combinedDateTime = LocalDateTime.of(parsedDate, parsedTime);

                // Cria o objeto de requisição utilizando os campos separados
                AvailabilityRequest request = AvailabilityRequest.builder()
                        .appointmentDate(parsedDate)
                        .appointmentTime(parsedTime)
                        .durationMinutes(60) // Duração padrão de 60 minutos
                        .build();

                // Chama o serviço para verificar disponibilidade
                AvailabilityResponse response = appointmentService.checkAvailability(token, request);

                return Map.of(
                        "success", true,
                        "appointmentDate", appointmentDate,
                        "appointmentTime", appointmentTime,
                        "available", response.isAvailable(),
                        "message", response.getMessage() != null ? response.getMessage()
                                : (response.isAvailable() ? "Horário disponível" : "Horário indisponível")
                );

            } catch (Exception e) {
                logger.error("Erro ao verificar disponibilidade: {}", e.getMessage());
                return Map.of(
                        "success", false,
                        "error", "Erro ao verificar disponibilidade: " + e.getMessage()
                );
            }
        }

        @Tool(description = "Cria um novo agendamento com data, horário, nome e resumo.")
        public Map<String, Object> CreateAppointment(String id,String appointmentDate, String appointmentTime, String name, String summary) {
            try {
                String token = resolveToken();
                if (token == null) {
                    return Map.of(
                            "success", false,
                            "error", "Não foi possível identificar o token. Verifique a autenticação."
                    );
                }

                // Converte a string de data e a string de horário para LocalDate e LocalTime no fuso horário de São Paulo (Brazil)
                DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
                
                // Usa ZoneId.of("America/Sao_Paulo") para garantir que estamos usando o horário de São Paulo
                ZoneId brazilZone = ZoneId.of("America/Sao_Paulo");
                LocalDate parsedDate = LocalDate.parse(appointmentDate, dateFormatter);
                LocalTime parsedTime = LocalTime.parse(appointmentTime, timeFormatter);

                // Cria o objeto de requisição com os campos separados
                AppointmentRequest request = AppointmentRequest.builder()
                        .id(id)
                        .appointmentDate(parsedDate)
                        .appointmentTime(parsedTime)
                        .name(name)
                        .summary(summary)
                        .durationMinutes(60) // Duração padrão de 60 minutos
                        .build();

                // Chama o serviço para criar o agendamento
                AppointmentDto appointment = appointmentService.createAppointment(token, request);

                // Monta a resposta com os dados do agendamento criado
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("id", appointment.getId());
                response.put("appointmentDate", appointment.getAppointmentDate().toString());
                response.put("name", appointment.getName());
                response.put("summary", appointment.getSummary());
                response.put("status", appointment.getStatus());

                return response;

            } catch (Exception e) {
                logger.error("Erro ao criar agendamento: {}", e.getMessage());
                return Map.of(
                        "success", false,
                        "error", "Erro ao criar agendamento: " + e.getMessage()
                );
            }
        }

        @Tool(description = "Obtém os detalhes de um agendamento específico pelo seu ID.")
        public Map<String, Object> GetAppointmentDetails(String id) {
            try {
                String token = resolveToken();
                if (token == null) {
                    return Map.of(
                            "success", false,
                            "error", "Não foi possível identificar o token. Verifique a autenticação."
                    );
                }

                // Valida o ID do agendamento
                if (id == null || id.trim().isEmpty()) {
                    return Map.of(
                            "success", false,
                            "error", "O ID do agendamento é obrigatório."
                    );
                }

                // Chama o serviço para obter os detalhes do agendamento
                AppointmentDto appointment = appointmentService.getAppointment(token, id);

                // Define um formatter para formatação consistente de datas e horas no fuso horário de São Paulo (Brazil)
                ZoneId brazilZone = ZoneId.of("America/Sao_Paulo");
                DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(brazilZone);

                // Monta a resposta com os dados do agendamento
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("id", appointment.getId());
                response.put("appointmentDate", appointment.getAppointmentDate() != null
                        ? appointment.getAppointmentDate().format(formatter)
                        : null);
                response.put("endDate", appointment.getEndDate() != null
                        ? appointment.getEndDate().format(formatter)
                        : null);
                response.put("durationMinutes", 60);
                response.put("name", appointment.getName());
                response.put("summary", appointment.getSummary());
                response.put("status", appointment.getStatus());
                response.put("createdAt", appointment.getCreatedAt() != null
                        ? appointment.getCreatedAt().format(formatter)
                        : null);
                response.put("updatedAt", appointment.getUpdatedAt() != null
                        ? appointment.getUpdatedAt().format(formatter)
                        : null);

                return response;

            } catch (Exception e) {
                logger.error("Erro ao obter detalhes do agendamento: {}", e.getMessage());
                return Map.of(
                        "success", false,
                        "error", "Erro ao obter detalhes do agendamento: " + e.getMessage()
                );
            }
        }

        @Tool(description = "Reagenda um agendamento existente com nova data, horário, duração, nome e resumo.")
        public Map<String, Object> RescheduleAppointment(String id, String appointmentDate, String appointmentTime , String name, String summary) {
            try {
                String token = resolveToken();
                if (token == null) {
                    return Map.of(
                            "success", false,
                            "error", "Não foi possível identificar o token. Verifique a autenticação."
                    );
                }

                // Validação do ID
                if (id == null || id.trim().isEmpty()) {
                    return Map.of(
                            "success", false,
                            "error", "O ID do agendamento é obrigatório."
                    );
                }

                // Converte as strings de data e horário para LocalDate e LocalTime no fuso horário de São Paulo (Brazil)
                DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
                
                // Usa ZoneId.of("America/Sao_Paulo") para garantir que estamos usando o horário de São Paulo
                ZoneId brazilZone = ZoneId.of("America/Sao_Paulo");
                LocalDate parsedDate = LocalDate.parse(appointmentDate, dateFormatter);
                LocalTime parsedTime = LocalTime.parse(appointmentTime, timeFormatter);

                // Cria o objeto de requisição para reagendamento
                AppointmentRequest request = AppointmentRequest.builder()
                        .id(id)
                        .appointmentDate(parsedDate)
                        .appointmentTime(parsedTime)
                        .durationMinutes(60)
                        .name(name)
                        .summary(summary)
                        .build();

                // Chama o serviço para reagendar o agendamento
                AppointmentDto appointment = appointmentService.rescheduleAppointment(token, id, request);

                // Monta a resposta com os dados do agendamento reagendado
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("id", appointment.getId());
                response.put("appointmentDate", appointment.getAppointmentDate().toString());
                response.put("endDate", appointment.getEndDate() != null ? appointment.getEndDate().toString() : null);
                response.put("durationMinutes", 60);
                response.put("name", appointment.getName());
                response.put("summary", appointment.getSummary());
                response.put("status", appointment.getStatus());

                return response;
            } catch (Exception e) {
                logger.error("Erro ao reagendar agendamento: {}", e.getMessage());
                return Map.of(
                        "success", false,
                        "error", "Erro ao reagendar agendamento: " + e.getMessage()
                );
            }
        }



        @Tool(description = "Verifica a disponibilidade para agendamentos em uma faixa de horário específica.")
        public Map<String, Object> CheckAvailabilityRange(String appointmentDate, String startTime, String endTime) {
            try {
                String token = resolveToken();
                if (token == null) {
                    return Map.of(
                            "success", false,
                            "error", "Não foi possível identificar o token. Verifique a autenticação."
                    );
                }

                // Converte as strings de data e horários para LocalDate e LocalTime no fuso horário de São Paulo (Brazil)
                DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
                
                // Usa ZoneId.of("America/Sao_Paulo") para garantir que estamos usando o horário de São Paulo
                ZoneId brazilZone = ZoneId.of("America/Sao_Paulo");
                LocalDate parsedDate = LocalDate.parse(appointmentDate, dateFormatter);
                LocalTime parsedStartTime = LocalTime.parse(startTime, timeFormatter);
                LocalTime parsedEndTime = LocalTime.parse(endTime, timeFormatter);

                // Cria o objeto de requisição de range
                AvailabilityRangeRequestDto request = AvailabilityRangeRequestDto.builder()
                        .appointmentDate(parsedDate)
                        .startTime(parsedStartTime)
                        .endTime(parsedEndTime)
                        .durationMinutes(60) // Duração fixa de 60 minutos
                        .intervalMinutes(60) // Intervalo fixo de 60 minutos
                        .build();

                // Chama o serviço para verificar disponibilidade por range
                AvailabilityRangeResponseDto response = appointmentService.checkAvailabilityRange(token, request);

                // Cria um mapa para a resposta
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("appointmentDate", appointmentDate);
                result.put("startTime", startTime);
                result.put("endTime", endTime);
                
                // Formata os slots disponíveis para o formato esperado
                List<Map<String, String>> formattedSlots = new ArrayList<>();
                if (response.getAvailableSlots() != null) {
                    for (TimeSlot slot : response.getAvailableSlots()) {
                        Map<String, String> formattedSlot = new HashMap<>();
                        formattedSlot.put("startTime", slot.getStartTime().toString());
                        formattedSlot.put("endTime", slot.getEndTime().toString());
                        formattedSlots.add(formattedSlot);
                    }
                }
                
                result.put("availableSlots", formattedSlots);
                result.put("message", response.getMessage() != null ? response.getMessage() : 
                        String.format("Encontrados %d horários disponíveis", formattedSlots.size()));

                return result;

            } catch (Exception e) {
                logger.error("Erro ao verificar disponibilidade por range: {}", e.getMessage());
                return Map.of(
                        "success", false,
                        "error", "Erro ao verificar disponibilidade por range: " + e.getMessage()
                );
            }
        }

        @Tool(description = "Cancela um agendamento existente pelo seu ID.")
        public Map<String, Object> CancelAppointment(String id) {
            try {
                String token = resolveToken();
                if (token == null) {
                    return Map.of(
                            "success", false,
                            "error", "Não foi possível identificar o token. Verifique a autenticação."
                    );
                }

                // Valida o ID do agendamento
                if (id == null || id.trim().isEmpty()) {
                    return Map.of(
                            "success", false,
                            "error", "O ID do agendamento é obrigatório."
                    );
                }

                // Chama o serviço para cancelar o agendamento
                boolean cancelled = appointmentService.cancelAppointment(token, id);

                return Map.of(
                        "success", true,
                        "id", id,
                        "cancelled", cancelled,
                        "message", "Agendamento cancelado com sucesso"
                );

            } catch (Exception e) {
                logger.error("Erro ao cancelar agendamento: {}", e.getMessage());
                return Map.of(
                        "success", false,
                        "error", "Erro ao cancelar agendamento: " + e.getMessage()
                );
            }
        }
    }
}
package chatgm.com.mcp_server.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * DTO para requisição de verificação de disponibilidade
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityRequest {
    @NotNull(message = "Appointment date is required")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate appointmentDate;  // Exemplo: "2025-04-05"

    @NotNull(message = "Appointment time is required")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime appointmentTime;  // Exemplo: "14:30"

    private Integer durationMinutes = 60; // Duração padrão: 60 minutos
}
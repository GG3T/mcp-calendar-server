package chatgm.com.mcp_server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuração para habilitar agendamento de tarefas (scheduler)
 * Usado para healthchecks periódicos e heartbeats
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {
    // A configuração habilita o agendamento de tarefas no Spring
    // As tarefas propriamente ditas estão implementadas nos serviços como SseHealthService
}

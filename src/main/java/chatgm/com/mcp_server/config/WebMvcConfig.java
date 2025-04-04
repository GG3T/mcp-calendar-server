package chatgm.com.mcp_server.config;

import chatgm.com.mcp_server.controller.McpInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final McpInterceptor mcpInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Registramos nosso interceptor para capturar todas as requisições MCP
        registry.addInterceptor(mcpInterceptor).addPathPatterns("/mcp/**");
    }
}
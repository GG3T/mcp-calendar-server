package chatgm.com.mcp_server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Value("${webclient.timeout.connect:30000}")
    private int connectTimeout;

    @Value("${webclient.timeout.read:30000}")
    private int readTimeout;

    @Bean
    @Primary
    public RestTemplate restTemplate() {
        // Usando SimpleClientHttpRequestFactory em vez de HttpComponentsClientHttpRequestFactory
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        
        // Criando RestTemplate com a factory configurada
        RestTemplate restTemplate = new RestTemplate(factory);
        
        return restTemplate;
    }
}

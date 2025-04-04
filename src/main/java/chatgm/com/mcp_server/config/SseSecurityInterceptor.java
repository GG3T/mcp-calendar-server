package chatgm.com.mcp_server.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@Slf4j
@RequiredArgsConstructor
public class SseSecurityInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        log.debug("Interceptando requisição para: {}", request.getRequestURI());

        // Verifica apenas requisições para o endpoint SSE
        if (request.getRequestURI().endsWith("/sse")) {
            // Tenta obter o token do header ou do parâmetro da URL
            String token = request.getHeader("Authorization");
            String tokenParam = request.getParameter("token");
            
            if ((token == null || token.isEmpty()) && (tokenParam == null || tokenParam.isEmpty())) {
                log.error("Acesso não autorizado ao SSE: Token não fornecido");
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.getWriter().write("Não autorizado: Forneça o token via header Authorization ou parâmetro 'token' na URL");
                return false;
            }
            
            // Se o parâmetro token estiver definido, use-o
            if (token == null || token.isEmpty()) {
                token = tokenParam;
            }

            log.debug("Requisição SSE autorizada para processamento com token: {}", token);
        }
        
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
        // Nada a fazer após o processamento
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // Nada a fazer após a conclusão
    }
}
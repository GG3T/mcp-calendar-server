package chatgm.com.mcp_server.config;

import chatgm.com.mcp_server.exception.UsuarioNaoAutorizadoException;
import chatgm.com.mcp_server.repository.UsuarioRepository;
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

    private final UsuarioRepository usuarioRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        log.debug("Interceptando requisição para: {}", request.getRequestURI());

        // Verifica apenas requisições para o endpoint SSE
        if (request.getRequestURI().endsWith("/sse")) {
            // Tenta obter o email do header ou do parâmetro da URL
            String email = request.getHeader("Authorization");
            String emailParam = request.getParameter("email");
            
            if ((email == null || email.isEmpty()) && (emailParam == null || emailParam.isEmpty())) {
                log.error("Acesso não autorizado ao SSE: Email não fornecido");
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.getWriter().write("Não autorizado: Forneça o email via header Authorization ou parâmetro 'email' na URL");
                return false;
            }
            
            // Se o parâmetro email estiver definido, use-o
            if (email == null || email.isEmpty()) {
                email = emailParam;
            }
            
            // Verifica se o email existe no banco de dados
            boolean emailExiste = usuarioRepository.findByEmail(email).isPresent();
            
            if (!emailExiste) {
                log.error("Acesso não autorizado ao SSE: Email não cadastrado: {}", email);
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.getWriter().write("Não autorizado: Email não cadastrado");
                return false;
            }
            
            log.debug("Requisição SSE autorizada para processamento com email: {}", email);
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

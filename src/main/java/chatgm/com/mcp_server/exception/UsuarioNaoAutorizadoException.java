package chatgm.com.mcp_server.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UsuarioNaoAutorizadoException extends RuntimeException {
    
    public UsuarioNaoAutorizadoException(String email) {
        super("Usuário não autorizado: " + email);
    }
}

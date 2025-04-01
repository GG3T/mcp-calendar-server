package chatgm.com.mcp_server.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class TokenInvalidoException extends RuntimeException {
    
    public TokenInvalidoException(String message) {
        super(message);
    }
    
    public TokenInvalidoException(String message, Throwable cause) {
        super(message, cause);
    }
}

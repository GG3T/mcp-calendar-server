package chatgm.com.mcp_server.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tokens_oauth")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenOauth {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "access_token", nullable = false)
    private String accessToken;
    
    @Column(name = "refresh_token")
    private String refreshToken;
    
    @Column(name = "token_type")
    private String tokenType;
    
    @Column(name = "expires_in")
    private Long expiresIn;
    
    private String scope;
    
    @Column(name = "data_criacao")
    private LocalDateTime dataCriacao;
    
    @Column(name = "data_expiracao")
    private LocalDateTime dataExpiracao;
    
    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    
    /**
     * Verifica se o token está expirado
     * @return true se o token expirou, false caso contrário
     */
    public boolean isExpirado() {
        return LocalDateTime.now().isAfter(dataExpiracao);
    }
}

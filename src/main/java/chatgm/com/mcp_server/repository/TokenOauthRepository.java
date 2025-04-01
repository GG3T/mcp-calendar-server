package chatgm.com.mcp_server.repository;

import chatgm.com.mcp_server.model.TokenOauth;
import chatgm.com.mcp_server.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TokenOauthRepository extends JpaRepository<TokenOauth, Long> {
    
    /**
     * Encontra tokens associados a um usuário específico
     * @param usuario o usuário para o qual buscar os tokens
     * @return Optional contendo o token mais recente, se encontrado
     */
    Optional<TokenOauth> findTopByUsuarioOrderByDataCriacaoDesc(Usuario usuario);
}

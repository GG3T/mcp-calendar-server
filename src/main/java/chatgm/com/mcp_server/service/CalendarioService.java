package chatgm.com.mcp_server.service;

import chatgm.com.mcp_server.exception.TokenInvalidoException;
import chatgm.com.mcp_server.exception.UsuarioNaoEncontradoException;
import chatgm.com.mcp_server.model.TokenOauth;
import chatgm.com.mcp_server.model.Usuario;
import chatgm.com.mcp_server.repository.TokenOauthRepository;
import chatgm.com.mcp_server.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CalendarioService {

    private final UsuarioRepository usuarioRepository;
    private final TokenOauthRepository tokenOauthRepository;
    private final GoogleCalendarService googleCalendarService;

    /**
     * Obtém a lista de calendários do usuário pelo email
     * @param email o email do usuário
     * @return lista de IDs dos calendários
     * @throws UsuarioNaoEncontradoException se o usuário não for encontrado
     * @throws TokenInvalidoException se não houver token válido para o usuário
     */
    public List<String> getCalendariosByUserEmail(String email) {
        log.info("Buscando calendários para o usuário com email: {}", email);
        
        // Busca o usuário pelo email
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> {
                    log.error("Usuário não encontrado com email: {}", email);
                    return new UsuarioNaoEncontradoException(email);
                });
        
        log.info("Usuário encontrado: ID = {}, Nome = {}", usuario.getId(), usuario.getNomeCompleto());
        
        // Busca o token mais recente para o usuário
        TokenOauth tokenOauth = tokenOauthRepository.findTopByUsuarioOrderByDataCriacaoDesc(usuario)
                .orElseThrow(() -> {
                    log.error("Nenhum token OAuth encontrado para o usuário: {}", email);
                    return new TokenInvalidoException("Nenhum token OAuth encontrado para o usuário");
                });
        
        // Verifica se o token está expirado
        if (tokenOauth.isExpirado()) {
            log.warn("Token OAuth expirado para o usuário: {}", email);
            // Aqui poderia ir a lógica para renovação do token usando refresh_token
            throw new TokenInvalidoException("Token OAuth expirado e renovação não implementada");
        }
        
        log.info("Token OAuth válido encontrado para o usuário: {}", email);
        
        // Busca a lista de calendários através da API do Google
        return googleCalendarService.getCalendarList(tokenOauth);
    }
}

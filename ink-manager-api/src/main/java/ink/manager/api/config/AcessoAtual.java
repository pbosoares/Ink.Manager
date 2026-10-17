package ink.manager.api.config;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
@Component
public class AcessoAtual {
    private JwtAuthenticationToken autenticacao() {
        return (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
    }
    public Long estudioId() { return ((Number) autenticacao().getToken().getClaim("estudioId")).longValue(); }
    public Long usuarioId() { return ((Number) autenticacao().getToken().getClaim("usuarioId")).longValue(); }
}

package ink.manager.api.config;

import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import ink.manager.api.repository.UsuarioRepository;

@Component
public class SessionJwtConverter implements Converter<Jwt,AbstractAuthenticationToken>{
    private final UsuarioRepository usuarios;
    public SessionJwtConverter(UsuarioRepository usuarios){this.usuarios=usuarios;}
    @Override public AbstractAuthenticationToken convert(Jwt jwt){
        try{
            long id=((Number)jwt.getClaim("usuarioId")).longValue();
            long estudioId=((Number)jwt.getClaim("estudioId")).longValue();
            long version=((Number)jwt.getClaim("tokenVersion")).longValue();
            var usuario=usuarios.findByIdAndEstudioId(id,estudioId).orElseThrow();
            if(!usuario.isAtivo() || usuario.getTokenVersion()!=version) throw new IllegalArgumentException();
            return new JwtAuthenticationToken(jwt,List.of(new SimpleGrantedAuthority("ROLE_"+usuario.getPerfil().name())));
        }catch(RuntimeException invalid){
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"),"Sessao invalida ou revogada.");
        }
    }
}

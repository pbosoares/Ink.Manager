package ink.manager.api.service;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ink.manager.api.config.AcessoAtual;
import ink.manager.api.dto.*;
import ink.manager.api.exception.ApiException;
import ink.manager.api.model.*;
import ink.manager.api.repository.*;

@Service
@Transactional
public class UsuarioService {
    private final UsuarioRepository usuarios;
    private final EstudioRepository estudios;
    private final PasswordEncoder senhas;
    private final JwtEncoder tokens;
    private final AcessoAtual acesso;
    private final InicializacaoEstudio inicializacao;
    private final AgendaGuard guard;
    private final Clock clock;

    public UsuarioService(UsuarioRepository usuarios,EstudioRepository estudios,PasswordEncoder senhas,
            JwtEncoder tokens,AcessoAtual acesso,InicializacaoEstudio inicializacao,AgendaGuard guard,Clock clock){
        this.usuarios=usuarios; this.estudios=estudios; this.senhas=senhas; this.tokens=tokens;
        this.acesso=acesso; this.inicializacao=inicializacao; this.guard=guard; this.clock=clock;
    }
    public Usuario novoEstudio(NovoEstudioRequest request){
        validarNovoEmail(request.email());
        var estudio=new Estudio(); estudio.setNome(request.nomeEstudio().trim()); estudio.setCriadoEm(LocalDateTime.now(clock)); estudio=estudios.saveAndFlush(estudio);
        var usuario=criar(request.nome(),request.email(),request.senha(),estudio,Perfil.PROPRIETARIO);
        inicializacao.criarAgenda(estudio);
        return usuario;
    }
    public Usuario novoFuncionario(NovoFuncionarioRequest request){
        guard.bloquear(); validarNovoEmail(request.email());
        return criar(request.nome(),request.email(),request.senha(),estudios.getReferenceById(acesso.estudioId()),Perfil.FUNCIONARIO);
    }
    private Usuario criar(String nome,String email,String senha,Estudio estudio,Perfil perfil){
        var usuario=new Usuario(); usuario.setNome(nome.trim()); usuario.setEmail(email.trim().toLowerCase(Locale.ROOT));
        usuario.setSenha(hashSenha(senha)); usuario.setEstudio(estudio); usuario.setPerfil(perfil);
        return usuarios.saveAndFlush(usuario);
    }
    private void validarNovoEmail(String email){
        if(usuarios.findByEmailIgnoreCase(email.trim()).isPresent()) throw new ApiException(HttpStatus.CONFLICT,"E-mail ja cadastrado.");
    }
    public String hashSenha(String senha){
        if(senha.getBytes(StandardCharsets.UTF_8).length>72) throw new ApiException(HttpStatus.BAD_REQUEST,"Senha deve ter no maximo 72 bytes UTF-8.");
        return senhas.encode(senha);
    }
    @Transactional(readOnly=true)
    public String login(String email,String senha){
        var usuario=usuarios.findByEmailIgnoreCase(email.trim()).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"E-mail ou senha invalidos."));
        if(!usuario.isAtivo() || senha.getBytes(StandardCharsets.UTF_8).length>72 || !senhas.matches(senha,usuario.getSenha()))
            throw new ApiException(HttpStatus.UNAUTHORIZED,"E-mail ou senha invalidos.");
        Instant agora=clock.instant();
        var claims=JwtClaimsSet.builder().issuer("ink-manager").subject(usuario.getId().toString())
            .issuedAt(agora).expiresAt(agora.plus(2,ChronoUnit.HOURS))
            .claim("usuarioId",usuario.getId()).claim("estudioId",usuario.getEstudio().getId())
            .claim("tokenVersion",usuario.getTokenVersion()).build();
        return tokens.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
    @Transactional(readOnly=true)
    public Usuario atual(){return usuarios.findByIdAndEstudioId(acesso.usuarioId(),acesso.estudioId()).orElseThrow();}
    public void sair(){var usuario=atual(); usuario.revogarSessoes(); usuarios.saveAndFlush(usuario);}
    @Transactional(readOnly=true)
    public List<UsuarioResponse> equipe(){return usuarios.findByEstudioIdOrderByNomeAsc(acesso.estudioId()).stream().map(UsuarioResponse::from).toList();}
    public UsuarioResponse alterarAcesso(Long id,boolean ativo,Long version){
        guard.bloquear();
        var usuario=usuarios.findByIdAndEstudioId(id,acesso.estudioId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Usuario nao encontrado."));
        if(version==null || !Objects.equals(version,usuario.getVersion())) throw new ApiException(HttpStatus.CONFLICT,"Conta alterada. Recarregue a pagina.");
        if(id.equals(acesso.usuarioId()) && !ativo) throw new ApiException(HttpStatus.CONFLICT,"O responsavel nao pode desativar a propria conta.");
        usuario.setAtivo(ativo); usuario.revogarSessoes(); return UsuarioResponse.from(usuarios.saveAndFlush(usuario));
    }
    public void revogar(Long id){
        guard.bloquear();
        var usuario=usuarios.findByIdAndEstudioId(id,acesso.estudioId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Usuario nao encontrado."));
        usuario.revogarSessoes(); usuarios.saveAndFlush(usuario);
    }
}

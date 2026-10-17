package ink.manager.api.service;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ink.manager.api.exception.ApiException;
import ink.manager.api.model.RecuperacaoSenha;
import ink.manager.api.repository.*;

@Service @Transactional
public class RecuperacaoSenhaService {
    private final UsuarioRepository usuarios;
    private final EstudioRepository estudios;
    private final RecuperacaoSenhaRepository recuperacoes;
    private final RecuperacaoEmail email;
    private final UsuarioService contas;
    private final Clock clock;
    private final String publicUrl;
    public RecuperacaoSenhaService(UsuarioRepository usuarios,EstudioRepository estudios,RecuperacaoSenhaRepository recuperacoes,
            RecuperacaoEmail email,UsuarioService contas,Clock clock,@Value("${app.public-url:http://localhost:8080}") String publicUrl){
        this.usuarios=usuarios;this.estudios=estudios;this.recuperacoes=recuperacoes;this.email=email;this.contas=contas;this.clock=clock;this.publicUrl=publicUrl;
    }
    public void solicitar(String endereco){
        if(!email.disponivel()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Recuperacao por e-mail indisponivel. Tente novamente mais tarde.");
        var usuario=usuarios.findByEmailIgnoreCase(endereco.trim()).orElse(null);
        if(usuario==null||!usuario.isAtivo()) return;
        estudios.bloquear(usuario.getEstudio().getId()).orElseThrow();
        recuperacoes.deleteByUsuarioId(usuario.getId());recuperacoes.flush();
        byte[] random=new byte[32];new SecureRandom().nextBytes(random);String token=HexFormat.of().formatHex(random);
        recuperacoes.saveAndFlush(new RecuperacaoSenha(hash(token),usuario,LocalDateTime.now(clock).plusMinutes(30)));
        try{email.enviar(usuario.getEmail(),publicUrl.replaceAll("/+$","")+"/#redefinir="+token);}
        catch(org.springframework.mail.MailException error){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Nao foi possivel enviar o e-mail. Tente novamente mais tarde.");}
    }
    public void redefinir(String token,String senha){
        String hash=hash(token);
        var previa=recuperacoes.findById(hash).orElseThrow(this::invalido);
        estudios.bloquear(previa.getUsuario().getEstudio().getId()).orElseThrow();
        var recuperacao=recuperacoes.bloquear(hash).orElseThrow(this::invalido);
        if(recuperacao.isConsumido()||!recuperacao.getPrazo().isAfter(LocalDateTime.now(clock))||!recuperacao.getUsuario().isAtivo()) throw invalido();
        var usuario=recuperacao.getUsuario();usuario.setSenha(contas.hashSenha(senha));usuario.revogarSessoes();
        usuarios.saveAndFlush(usuario);recuperacao.consumir();recuperacoes.saveAndFlush(recuperacao);
    }
    private ApiException invalido(){return new ApiException(HttpStatus.BAD_REQUEST,"Link invalido, expirado ou ja utilizado.");}
    private String hash(String value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}

package ink.manager.api.service;

import ink.manager.api.exception.ApiException;
import ink.manager.api.model.EmailActionToken;
import ink.manager.api.model.Marcacao;
import ink.manager.api.model.StatusMarcacao;
import ink.manager.api.repository.EmailActionTokenRepository;
import ink.manager.api.repository.EstudioRepository;
import ink.manager.api.repository.MarcacaoRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class EmailActionService {
    public record Links(String confirmar,String reagendar,String cancelar,Instant expiraEm) {}
    public record Previa(String servico,String profissional,LocalDate data,LocalTime horario,int duracaoMinutos,
            String fusoHorario,String acao,Instant expiraEm) {}
    public record Pedido(String token,String acao,LocalDate data,LocalTime horario) {}
    public record Resultado(String estado,LocalDate data,LocalTime horario,String fusoHorario) {}

    private static final SecureRandom RANDOM=new SecureRandom();
    private final EmailActionTokenRepository tokens;
    private final EstudioRepository estudios;
    private final MarcacaoRepository marcacoes;
    private final MarcacaoService marcacaoService;
    private final AgendaGuard guard;
    private final Clock clock;
    private final String publicUrl;

    public EmailActionService(EmailActionTokenRepository tokens,EstudioRepository estudios,MarcacaoRepository marcacoes,MarcacaoService marcacaoService,
            AgendaGuard guard,Clock clock,@Value("${app.public-url:http://localhost:8080}") String publicUrl){
        this.tokens=tokens;this.estudios=estudios;this.marcacoes=marcacoes;this.marcacaoService=marcacaoService;this.guard=guard;this.clock=clock;this.publicUrl=publicUrl.replaceAll("/+$","");
    }

    /** Token secrets exist only in this method's return value and outgoing message memory. */
    @Transactional public Links emitir(long estudioId,long marcacaoId){
        guard.bloquear(estudioId);
        var m=marcacoes.findByIdAndEstudioId(marcacaoId,estudioId).orElseThrow(()->indisponivel());
        if(m.getStatus()!=StatusMarcacao.AGENDADA&&m.getStatus()!=StatusMarcacao.CONFIRMADA)throw indisponivel();
        Instant agora=clock.instant(),expira=agora.plus(Duration.ofDays(3));
        tokens.invalidarNaoUsados(estudioId,marcacaoId);
        String confirmar=emitirUm(estudioId,marcacaoId,"CONFIRMAR",agora,expira);
        String reagendar=emitirUm(estudioId,marcacaoId,"REAGENDAR",agora,expira);
        String cancelar=emitirUm(estudioId,marcacaoId,"CANCELAR",agora,expira);
        String pagina=publicUrl+"/acao-email.html#token=";
        return new Links(pagina+confirmar,pagina+reagendar,pagina+cancelar,expira);
    }

    public Previa visualizar(String token){
        var row=tokens.findById(hash(token)).orElseThrow(this::indisponivel);
        validarToken(row);
        if (!estudios.existsByIdAndEncerradoEmIsNull(row.getEstudioId())) throw indisponivel();
        Marcacao m=marcacoes.findByIdAndEstudioId(row.getMarcacaoId(),row.getEstudioId()).orElseThrow(this::indisponivel);
        return new Previa(m.getServicoNome()==null?m.getDescricao():m.getServicoNome(),m.getProfissional().getNome(),
                m.getData(),m.getHorario(),m.getDuracaoMinutos(),m.getEstudio().getFusoHorario(),row.getAcao(),row.getExpiraEm());
    }

    @Transactional public Resultado executar(Pedido pedido){
        if(pedido==null||pedido.token()==null||pedido.token().length()<32||pedido.token().length()>128)throw indisponivel();
        String hash=hash(pedido.token());
        var locked=tokens.findById(hash).orElseThrow(this::indisponivel);
        guard.bloquear(locked.getEstudioId());
        locked=tokens.bloquear(hash).orElseThrow(this::indisponivel);
        validarToken(locked);
        if(!locked.getAcao().equals(pedido.acao()))throw indisponivel();
        Marcacao atualizado=marcacaoService.aplicarAcaoEmail(locked.getEstudioId(),locked.getMarcacaoId(),locked.getAcao(),pedido.data(),pedido.horario());
        locked.usar(clock.instant());
        tokens.invalidarNaoUsados(locked.getEstudioId(),locked.getMarcacaoId());
        return new Resultado(atualizado.getStatus().name(),atualizado.getData(),atualizado.getHorario(),atualizado.getEstudio().getFusoHorario());
    }

    private String emitirUm(long estudioId,long marcacaoId,String acao,Instant agora,Instant expira){
        byte[] raw=new byte[32];RANDOM.nextBytes(raw);String token=HexFormat.of().formatHex(raw);
        tokens.saveAndFlush(new EmailActionToken(hash(token),estudioId,marcacaoId,acao,agora,expira));return token;
    }
    private void validarToken(EmailActionToken t){
        if(t.getUsadoEm()!=null||!t.getExpiraEm().isAfter(clock.instant()))throw indisponivel();
    }
    private String hash(String value){
        if(value==null||value.length()<32||value.length()>128)throw indisponivel();
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
    private ApiException indisponivel(){return new ApiException(HttpStatus.GONE,"Este link expirou ou ja foi utilizado.");}
}

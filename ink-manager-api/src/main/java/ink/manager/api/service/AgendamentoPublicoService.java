package ink.manager.api.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ink.manager.api.exception.ApiException;
import ink.manager.api.model.*;
import ink.manager.api.repository.*;

/** Public access always receives an explicit establishment. No SecurityContext impersonation. */
@Service @Transactional(readOnly=true)
public class AgendamentoPublicoService {
    public record ServicoPublico(Long id,String nome,BigDecimal preco,Integer duracaoMinutos){}
    public record ProfissionalPublico(Long id,String nome){}
    public record Catalogo(String nome,String fusoHorario,LocalDate hoje,int antecedenciaMinimaMinutos,String versaoAvisoPrivacidade,List<ServicoPublico> servicos,List<ProfissionalPublico> profissionais){}
    public record Pedido(@NotNull UUID chave,@NotNull Long servicoId,@NotNull Long profissionalId,
        @NotNull LocalDate data,@NotNull LocalTime horario,
        @NotBlank @Size(max=100) String nome,
        @NotBlank @Pattern(regexp="(?=(?:[^0-9]*[0-9]){8,})[0-9+() .-]{8,30}",message="Informe um telefone valido.") String telefone,
        @NotBlank @jakarta.validation.constraints.Email @Size(max=254) String email,@AssertTrue boolean avisoPrivacidadeLido){}
    public record Confirmacao(String protocolo,String servico,String profissional,LocalDate data,LocalTime horario,String fusoHorario,BigDecimal preco,Integer duracaoMinutos){}
    private final EstudioRepository estudios;
    private final ServicoRepository servicos;
    private final ProfissionalRepository profissionais;
    private final ClienteRepository clientes;
    private final MarcacaoRepository marcacoes;
    private final DisponibilidadeService disponibilidade;
    private final AgendaGuard guard;
    private final AssinaturaService assinatura;
    private final Clock clock;
    public AgendamentoPublicoService(EstudioRepository estudios,ServicoRepository servicos,ProfissionalRepository profissionais,ClienteRepository clientes,MarcacaoRepository marcacoes,DisponibilidadeService disponibilidade,AgendaGuard guard,AssinaturaService assinatura,Clock clock){
        this.estudios=estudios;this.servicos=servicos;this.profissionais=profissionais;this.clientes=clientes;this.marcacoes=marcacoes;this.disponibilidade=disponibilidade;this.guard=guard;this.assinatura=assinatura;this.clock=clock;
    }
    private Estudio local(String link){return estudios.findByLinkPublico(link).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Link de agendamento nao encontrado."));}
    private Servico servico(Long estudio,Long id){return servicos.findByIdAndEstudioId(id,estudio).filter(Servico::isAtivo).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Servico indisponivel."));}
    public Catalogo catalogo(String link){
        var e=local(link);var zona=ZoneId.of(e.getFusoHorario());
        return new Catalogo(e.getNome(),zona.getId(),LocalDate.now(clock.withZone(zona)),e.getAntecedenciaMinimaMinutos(),"2026-10-18-v1",servicos.findByEstudioIdOrderByNomeAsc(e.getId()).stream().filter(Servico::isAtivo).map(s->new ServicoPublico(s.getId(),s.getNome(),s.getPreco(),s.getDuracaoMinutos())).toList(),profissionais.findByEstudioId(e.getId()).stream().filter(Profissional::isAtivo).map(p->new ProfissionalPublico(p.getId(),p.getNome())).toList());
    }
    private void validarData(LocalDate data,String fuso){
        var zona=ZoneId.of(fuso);var hoje=LocalDate.now(clock.withZone(zona));
        if(data.isBefore(hoje)||data.isAfter(hoje.plusDays(365)))throw new ApiException(HttpStatus.BAD_REQUEST,"Escolha um dia nos proximos 12 meses.");
    }
    public DisponibilidadeService.HorariosDisponiveis horarios(String link,Long servicoId,Long profissionalId,LocalDate data){
        var e=local(link);validarData(data,e.getFusoHorario());var s=servico(e.getId(),servicoId);
        if(!assinatura.podeGravar(e.getId()))throw new ApiException(HttpStatus.CONFLICT,"Agendamento temporariamente indisponivel. Fale com o estabelecimento.");
        var horarios=disponibilidade.consultarEstudio(e.getId(),profissionalId,data,s.getDuracaoMinutos(),null);
        var zona=ZoneId.of(e.getFusoHorario());var limite=ZonedDateTime.now(clock.withZone(zona)).plusMinutes(e.getAntecedenciaMinimaMinutos()).toLocalDateTime();
        var livres=horarios.horarios().stream().filter(h->!data.atTime(h).isBefore(limite)).toList();
        return new DisponibilidadeService.HorariosDisponiveis(horarios.profissionalId(),horarios.data(),horarios.duracaoMinutos(),zona.getId(),livres);
    }
    @Transactional public Confirmacao reservar(String link,Pedido r){
        var e=local(link);guard.bloquear(e.getId());
        String hash=hash(r);var anterior=marcacoes.findByEstudioIdAndChavePublica(e.getId(),r.chave().toString());
        if(anterior.isPresent()){
            if(!hash.equals(anterior.get().getPedidoPublicoHash()))throw new ApiException(HttpStatus.CONFLICT,"Pedido ja utilizado. Inicie outra reserva.");
            return resposta(anterior.get());
        }
        if(!assinatura.podeGravar(e.getId()))throw new ApiException(HttpStatus.CONFLICT,"Agendamento temporariamente indisponivel. Fale com o estabelecimento.");
        validarData(r.data(),e.getFusoHorario());var zona=ZoneId.of(e.getFusoHorario());var agora=ZonedDateTime.now(clock.withZone(zona));
        if(r.data().atTime(r.horario()).atZone(zona).isBefore(agora.plusMinutes(e.getAntecedenciaMinimaMinutos())))throw new ApiException(HttpStatus.CONFLICT,"Escolha um horario com mais antecedencia.");
        String email=r.email().trim().toLowerCase(Locale.ROOT);
        if(marcacoes.contarAgendamentosAbertosPorEmail(e.getId(),email,agora.toLocalDate(),agora.toLocalTime())>=e.getLimiteAgendamentosEmail())throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"Esse email ja tem o limite de agendamentos em aberto.");
        var s=servico(e.getId(),r.servicoId());var p=disponibilidade.profissional(e.getId(),r.profissionalId());
        if(!disponibilidade.consultarEstudio(e.getId(),p.getId(),r.data(),s.getDuracaoMinutos(),null).horarios().contains(r.horario()))throw new ApiException(HttpStatus.CONFLICT,"Esse horario acabou de ficar indisponivel. Escolha outro.");
        // Never match a public caller to an existing contact by phone: that would let strangers overwrite customer data.
        var c=new Cliente();c.setEstudio(e);c.setNome(r.nome().trim());c.setTelefone(r.telefone().trim());c.setEmail(email);c=clientes.save(c);
        var m=new Marcacao(c,r.data(),r.horario(),s.getNome(),StatusMarcacao.AGENDADA);
        m.setEstudio(e);m.setProfissional(p);m.setDuracaoMinutos(s.getDuracaoMinutos());m.definirServico(s);m.definirPedidoPublico(r.chave().toString(),hash);m.registrarAvisoPrivacidade("2026-10-18-v1",clock.instant());
        return resposta(marcacoes.saveAndFlush(m));
    }
    private Confirmacao resposta(Marcacao m){return new Confirmacao(m.getChavePublica(),m.getServicoNome(),m.getProfissional().getNome(),m.getData(),m.getHorario(),clock.getZone().getId(),m.getPrecoPrevisto(),m.getDuracaoMinutos());}
    private String hash(Pedido r){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((r.servicoId()+"\n"+r.profissionalId()+"\n"+r.data()+"\n"+r.horario()+"\n"+r.nome().trim()+"\n"+r.telefone().trim()+"\n"+r.email().trim().toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
}

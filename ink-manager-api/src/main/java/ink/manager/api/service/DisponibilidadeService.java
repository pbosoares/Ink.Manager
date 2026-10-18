package ink.manager.api.service;

import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ink.manager.api.config.AcessoAtual;
import ink.manager.api.dto.ProfissionalRequest;
import ink.manager.api.exception.ApiException;
import ink.manager.api.model.*;
import ink.manager.api.repository.*;

@Service
@Transactional(readOnly=true)
public class DisponibilidadeService {
    public record DiaDisponivel(LocalDate data,int quantidadeHorarios){}
    public record HorariosDisponiveis(Long profissionalId,LocalDate data,int duracaoMinutos,String fusoHorario,List<LocalTime> horarios){}
    private final ProfissionalRepository profissionais;
    private final HorarioTrabalhoRepository horarios;
    private final BloqueioAgendaRepository bloqueios;
    private final MarcacaoRepository marcacoes;
    private final AcessoAtual acesso;
    private final Clock clock;

    public DisponibilidadeService(ProfissionalRepository profissionais,HorarioTrabalhoRepository horarios,
            BloqueioAgendaRepository bloqueios,MarcacaoRepository marcacoes,AcessoAtual acesso,Clock clock){
        this.profissionais=profissionais;this.horarios=horarios;this.bloqueios=bloqueios;
        this.marcacoes=marcacoes;this.acesso=acesso;this.clock=clock;
    }
    public Profissional profissional(Long id){
        return profissional(acesso.estudioId(), id);
    }
    public Profissional profissional(Long estudioId, Long id){
        if(id==null) throw new ApiException(HttpStatus.BAD_REQUEST,"Informe o profissional.");
        return profissionais.findByIdAndEstudioId(id,estudioId).orElseThrow(()->
            new ApiException(HttpStatus.NOT_FOUND,"Profissional nao encontrado."));
    }
    public static boolean ativa(Marcacao m){return m.getStatus()==StatusMarcacao.AGENDADA||m.getStatus()==StatusMarcacao.CONFIRMADA;}
    public static boolean sobrepoe(LocalDateTime inicio,LocalDateTime fim,LocalDateTime outroInicio,LocalDateTime outroFim){
        return inicio.isBefore(outroFim)&&fim.isAfter(outroInicio);
    }
    public static boolean cabeNosTurnos(LocalDate data,LocalTime inicio,int duracao,List<ProfissionalRequest.Turno> turnos){
        LocalDateTime fim=data.atTime(inicio).plusMinutes(duracao);
        if(!fim.toLocalDate().equals(data)) return false;
        return turnos.stream().anyMatch(t->t.diaSemana()==data.getDayOfWeek().getValue()
            &&!inicio.isBefore(t.inicio())&&!fim.toLocalTime().isAfter(t.fim()));
    }
    private List<ProfissionalRequest.Turno> turnos(Long id){
        return horarios.findByProfissionalIdOrderByDiaSemanaAscInicioAsc(id).stream()
            .map(h->new ProfissionalRequest.Turno(h.getDiaSemana(),h.getInicio(),h.getFim())).toList();
    }
    private boolean livre(LocalDateTime inicio,int duracao,Long ignorarId,List<Marcacao> reservas,List<BloqueioAgenda> ausencias){
        var fim=inicio.plusMinutes(duracao);
        return reservas.stream().filter(DisponibilidadeService::ativa).filter(m->!Objects.equals(m.getId(),ignorarId))
            .noneMatch(m->sobrepoe(inicio,fim,m.getData().atTime(m.getHorario()),m.getData().atTime(m.getHorario()).plusMinutes(m.getDuracaoMinutos())))
            &&ausencias.stream().noneMatch(b->sobrepoe(inicio,fim,b.getInicio(),b.getFim()));
    }
    public void validarReserva(Marcacao m){
        var profissional=profissional(m.getProfissional().getId());
        if(!profissional.isAtivo()) throw new ApiException(HttpStatus.CONFLICT,"Profissional inativo.");
        if(!cabeNosTurnos(m.getData(),m.getHorario(),m.getDuracaoMinutos(),turnos(profissional.getId())))
            throw new ApiException(HttpStatus.CONFLICT,"Horario fora do expediente do profissional.");
        var reservas=marcacoes.findByProfissionalIdAndEstudioIdAndDataBetween(profissional.getId(),acesso.estudioId(),m.getData(),m.getData());
        if(!livre(m.getData().atTime(m.getHorario()),m.getDuracaoMinutos(),m.getId(),reservas,bloqueios.findByProfissionalIdOrderByInicioAsc(profissional.getId())))
            throw new ApiException(HttpStatus.CONFLICT,"Horario ocupado ou bloqueado para este profissional.");
    }
    public HorariosDisponiveis consultar(Long id,LocalDate data,int duracao,Long ignorarId){
        return consultarEstudio(acesso.estudioId(),id,data,duracao,ignorarId);
    }
    public HorariosDisponiveis consultarEstudio(Long estudioId,Long id,LocalDate data,int duracao,Long ignorarId){
        validarDuracao(duracao);var profissional=profissional(estudioId,id);
        if(ignorarId!=null&&!marcacoes.existsByIdAndProfissionalIdAndEstudioId(ignorarId,id,estudioId))
            throw new ApiException(HttpStatus.BAD_REQUEST,"Marcacao nao pertence ao profissional selecionado.");
        var reservas=marcacoes.findByProfissionalIdAndEstudioIdAndDataBetween(id,estudioId,data,data);
        var livres=slots(profissional,data,duracao,ignorarId,turnos(id),reservas,bloqueios.findByProfissionalIdOrderByInicioAsc(id));
        return new HorariosDisponiveis(id,data,duracao,clock.getZone().getId(),livres);
    }
    public List<DiaDisponivel> dias(Long id,LocalDate inicio,int dias,int duracao){
        validarDuracao(duracao);
        if(dias<1||dias>62) throw new ApiException(HttpStatus.BAD_REQUEST,"Consulte entre 1 e 62 dias.");
        var profissional=profissional(id);var turnos=turnos(id);
        var reservas=marcacoes.findByProfissionalIdAndEstudioIdAndDataBetween(id,acesso.estudioId(),inicio,inicio.plusDays(dias-1));
        var ausencias=bloqueios.findByProfissionalIdOrderByInicioAsc(id);var resultado=new ArrayList<DiaDisponivel>();
        for(int i=0;i<dias;i++){
            var data=inicio.plusDays(i);var reservasDoDia=reservas.stream().filter(m->m.getData().equals(data)).toList();
            resultado.add(new DiaDisponivel(data,slots(profissional,data,duracao,null,turnos,reservasDoDia,ausencias).size()));
        }
        return resultado;
    }
    private List<LocalTime> slots(Profissional profissional,LocalDate data,int duracao,Long ignorarId,
            List<ProfissionalRequest.Turno> turnos,List<Marcacao> reservas,List<BloqueioAgenda> ausencias){
        if(!profissional.isAtivo()) return List.of();
        var resultado=new TreeSet<LocalTime>();var agora=LocalDateTime.now(clock);
        for(var turno:turnos){
            if(turno.diaSemana()!=data.getDayOfWeek().getValue()) continue;
            for(var slot=data.atTime(turno.inicio());!slot.plusMinutes(duracao).isAfter(data.atTime(turno.fim()));slot=slot.plusMinutes(15)){
                if(!slot.isBefore(agora)&&livre(slot,duracao,ignorarId,reservas,ausencias)) resultado.add(slot.toLocalTime());
            }
        }
        return List.copyOf(resultado);
    }
    private void validarDuracao(int duracao){if(duracao<1||duracao>720) throw new ApiException(HttpStatus.BAD_REQUEST,"Duracao deve estar entre 1 e 720 minutos.");}
}

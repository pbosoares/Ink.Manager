package ink.manager.api.service;

import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ink.manager.api.config.AcessoAtual;
import ink.manager.api.dto.*;
import ink.manager.api.exception.ApiException;
import ink.manager.api.model.*;
import ink.manager.api.repository.*;

@Service @Transactional
public class ProfissionalService {
    private final ProfissionalRepository profissionais;
    private final HorarioTrabalhoRepository horarios;
    private final BloqueioAgendaRepository bloqueios;
    private final MarcacaoRepository marcacoes;
    private final EstudioRepository estudios;
    private final AgendaGuard guard;
    private final DisponibilidadeService disponibilidade;
    private final AcessoAtual acesso;
    private final Clock clock;
    public ProfissionalService(ProfissionalRepository profissionais,HorarioTrabalhoRepository horarios,BloqueioAgendaRepository bloqueios,
            MarcacaoRepository marcacoes,EstudioRepository estudios,AgendaGuard guard,DisponibilidadeService disponibilidade,AcessoAtual acesso,Clock clock){
        this.profissionais=profissionais;this.horarios=horarios;this.bloqueios=bloqueios;this.marcacoes=marcacoes;
        this.estudios=estudios;this.guard=guard;this.disponibilidade=disponibilidade;this.acesso=acesso;this.clock=clock;
    }
    @Transactional(readOnly=true) public List<ProfissionalResponse> listar(){
        var lista=profissionais.findByEstudioId(acesso.estudioId());
        if(lista.isEmpty())return List.of();
        var ids=lista.stream().map(Profissional::getId).toList();
        var porProfissional=new HashMap<Long,List<ProfissionalRequest.Turno>>();
        for(var horario:horarios.listarPorEstudioEProfissionaisOrdenado(acesso.estudioId(),ids))
            porProfissional.computeIfAbsent(horario.getProfissionalId(),ignored->new ArrayList<>())
                    .add(new ProfissionalRequest.Turno(horario.getDiaSemana(),horario.getInicio(),horario.getFim()));
        return lista.stream().map(p->new ProfissionalResponse(p.getId(),p.getNome(),p.isAtivo(),p.getVersion(),
                porProfissional.getOrDefault(p.getId(),List.of()))).toList();
    }
    private ProfissionalResponse resposta(Profissional p){return ProfissionalResponse.from(p,horarios.findByProfissionalIdOrderByDiaSemanaAscInicioAsc(p.getId()));}
    public ProfissionalResponse salvar(Long id,ProfissionalRequest request){
        guard.bloquear();var turnos=new ArrayList<>(request.horarios());
        turnos.sort(Comparator.comparingInt(ProfissionalRequest.Turno::diaSemana).thenComparing(ProfissionalRequest.Turno::inicio));
        ProfissionalRequest.Turno anterior=null;
        for(var turno:turnos){
            if(!turno.fim().isAfter(turno.inicio())) throw new ApiException(HttpStatus.BAD_REQUEST,"Fim do turno deve ser posterior ao inicio.");
            if(anterior!=null&&anterior.diaSemana()==turno.diaSemana()&&turno.inicio().isBefore(anterior.fim()))
                throw new ApiException(HttpStatus.BAD_REQUEST,"Turnos do mesmo dia nao podem se sobrepor.");
            anterior=turno;
        }
        var profissional=id==null?new Profissional():disponibilidade.profissional(id);
        if(id!=null){
            if(request.version()==null||!Objects.equals(profissional.getVersion(),request.version())) throw new ApiException(HttpStatus.CONFLICT,"Profissional alterado. Recarregue antes de salvar.");
            for(var reserva:marcacoes.findByProfissionalIdAndEstudioId(id,acesso.estudioId())){
                if(!DisponibilidadeService.ativa(reserva)||!reserva.getData().atTime(reserva.getHorario()).plusMinutes(reserva.getDuracaoMinutos()).isAfter(LocalDateTime.now(clock))) continue;
                if(!request.ativo()||!DisponibilidadeService.cabeNosTurnos(reserva.getData(),reserva.getHorario(),reserva.getDuracaoMinutos(),turnos))
                    throw new ApiException(HttpStatus.CONFLICT,"Existem sessoes afetadas. Reagende ou cancele antes de alterar o expediente.");
            }
        }
        profissional.setEstudio(estudios.getReferenceById(acesso.estudioId()));profissional.setNome(request.nome().trim());profissional.setAtivo(request.ativo());
        profissional.marcarConfiguracao();profissional=profissionais.saveAndFlush(profissional);horarios.deleteByProfissionalId(profissional.getId());horarios.flush();
        for(var turno:turnos) horarios.save(new HorarioTrabalho(profissional,turno.diaSemana(),turno.inicio(),turno.fim()));
        horarios.flush();return resposta(profissional);
    }
    @Transactional(readOnly=true) public List<BloqueioResponse> bloqueios(Long id){
        disponibilidade.profissional(id);return bloqueios.findByProfissionalIdOrderByInicioAsc(id).stream().map(BloqueioResponse::from).toList();
    }
    public BloqueioResponse bloquearPeriodo(Long id,BloqueioRequest request){
        guard.bloquear();var profissional=disponibilidade.profissional(id);
        if(!request.fim().isAfter(request.inicio())) throw new ApiException(HttpStatus.BAD_REQUEST,"Fim do bloqueio deve ser posterior ao inicio.");
        for(var reserva:marcacoes.findByProfissionalIdAndEstudioId(id,acesso.estudioId())){
            var inicio=reserva.getData().atTime(reserva.getHorario());
            if(DisponibilidadeService.ativa(reserva)&&DisponibilidadeService.sobrepoe(request.inicio(),request.fim(),inicio,inicio.plusMinutes(reserva.getDuracaoMinutos())))
                throw new ApiException(HttpStatus.CONFLICT,"Bloqueio conflita com sessao existente. Reagende ou cancele antes.");
        }
        return BloqueioResponse.from(bloqueios.saveAndFlush(new BloqueioAgenda(profissional,request.inicio(),request.fim(),request.motivo())));
    }
    public void removerBloqueio(Long id,Long bloqueioId){
        guard.bloquear();disponibilidade.profissional(id);
        var bloqueio=bloqueios.findByIdAndProfissionalId(bloqueioId,id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Bloqueio nao encontrado."));
        bloqueios.delete(bloqueio);
    }
}

package ink.manager.api.service;

import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ink.manager.api.config.AcessoAtual;
import ink.manager.api.dto.MarcacaoComClienteRequest;
import ink.manager.api.exception.ApiException;
import ink.manager.api.model.*;
import ink.manager.api.repository.*;

@Service @Transactional
public class MarcacaoService {
    private final MarcacaoRepository marcacoes;
    private final ClienteRepository clientes;
    private final EstudioRepository estudios;
    private final AgendaGuard guard;
    private final DisponibilidadeService disponibilidade;
    private final AcessoAtual acesso;
    private final Clock clock;
    private final RecebimentoRepository recebimentos;
    private final AgendamentoEmailService agendamentoEmail;
    private final ListaEsperaService listaEspera;
    public MarcacaoService(MarcacaoRepository marcacoes,ClienteRepository clientes,EstudioRepository estudios,
            AgendaGuard guard,DisponibilidadeService disponibilidade,AcessoAtual acesso,Clock clock,RecebimentoRepository recebimentos,
            AgendamentoEmailService agendamentoEmail,ListaEsperaService listaEspera){
        this.marcacoes=marcacoes;this.clientes=clientes;this.estudios=estudios;this.guard=guard;
        this.disponibilidade=disponibilidade;this.acesso=acesso;this.clock=clock;this.recebimentos=recebimentos;
        this.agendamentoEmail=agendamentoEmail;this.listaEspera=listaEspera;
    }
    @Transactional(readOnly=true) public List<Marcacao> listarTodas(){
        var lista=marcacoes.findByEstudioIdOrderByDataAscHorarioAscIdAsc(acesso.estudioId());
        var confirmadas=new HashSet<>(recebimentos.marcacoesConfirmadas(acesso.estudioId()));
        lista.forEach(m->informarRecebimento(m,confirmadas.contains(m.getId())));return lista;
    }
    @Transactional(readOnly=true) public Marcacao buscarPorId(Long id){
        var m=marcacoes.findByIdAndEstudioId(id,acesso.estudioId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Marcacao nao encontrada."));
        informarRecebimento(m,recebimentos.existsByMarcacaoIdAndEstudioId(id,acesso.estudioId()));return m;
    }
    public Marcacao salvar(Marcacao marcacao){
        guard.bloquear();marcacao.setId(null);marcacao.setVersion(null);resolverVinculos(marcacao);validarAgenda(marcacao,null);
        Marcacao criada=marcacoes.saveAndFlush(marcacao);agendamentoEmail.sincronizar(criada,null,null);return criada;
    }
    public Marcacao salvarComCliente(Long id,MarcacaoComClienteRequest request){
        guard.bloquear();var atual=id==null?null:buscarPorId(id);
        Cliente cliente;
        if(atual==null){
            cliente=request.cliente().toModel();cliente.setId(null);cliente.setVersion(null);
            cliente.setEstudio(estudios.getReferenceById(acesso.estudioId()));cliente=clientes.save(cliente);
        }else cliente=atual.getCliente();
        var marcacao=new Marcacao(cliente,request.data(),request.horario(),request.descricao(),request.status());
        marcacao.setProfissional(disponibilidade.profissional(request.profissionalId()));
        marcacao.setEstudio(estudios.getReferenceById(acesso.estudioId()));
        marcacao.setDuracaoMinutos(request.duracaoMinutos()==null?60:request.duracaoMinutos());marcacao.setVersion(request.version());
        if(atual!=null) return atualizarInterno(atual,marcacao);
        validarAgenda(marcacao,null);Marcacao criada=marcacoes.saveAndFlush(marcacao);agendamentoEmail.sincronizar(criada,null,null);return criada;
    }
    public Marcacao atualizar(Long id,Marcacao marcacao){guard.bloquear();return atualizarInterno(buscarPorId(id),marcacao);}
    private Marcacao atualizarInterno(Marcacao atual,Marcacao marcacao){
        protegerRecebimento(atual.getId());
        if(marcacao.getVersion()==null||!Objects.equals(atual.getVersion(),marcacao.getVersion())) throw new ApiException(HttpStatus.CONFLICT,"Registro alterado. Recarregue antes de salvar.");
        var inicioAnterior=atual.getData().atTime(atual.getHorario());var statusAnterior=atual.getStatus();
        marcacao.setId(atual.getId());resolverVinculos(marcacao);validarAgenda(marcacao,atual);
        atual.setCliente(marcacao.getCliente());atual.setProfissional(marcacao.getProfissional());atual.setData(marcacao.getData());
        atual.setHorario(marcacao.getHorario());atual.setDescricao(marcacao.getDescricao());atual.setStatus(marcacao.getStatus());atual.setDuracaoMinutos(marcacao.getDuracaoMinutos());
        Marcacao salva=marcacoes.saveAndFlush(atual);agendamentoEmail.sincronizar(salva,inicioAnterior,statusAnterior);
        if(statusAnterior!=StatusMarcacao.CANCELADA&&salva.getStatus()==StatusMarcacao.CANCELADA)listaEspera.processarCancelamento(salva.getEstudio().getId(),salva.getId());
        return salva;
    }
    private void resolverVinculos(Marcacao m){
        if(m.getCliente()==null||m.getCliente().getId()==null||m.getCliente().getId()<=0) throw new ApiException(HttpStatus.BAD_REQUEST,"Informe um cliente valido.");
        m.setCliente(clientes.findByIdAndEstudioId(m.getCliente().getId(),acesso.estudioId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Cliente nao encontrado.")));
        m.setProfissional(disponibilidade.profissional(m.getProfissional()==null?null:m.getProfissional().getId()));
        m.setEstudio(estudios.getReferenceById(acesso.estudioId()));
    }
    private void validarAgenda(Marcacao m,Marcacao anterior){
        int duracao=m.getDuracaoMinutos();
        if(duracao<1||duracao>720) throw new ApiException(HttpStatus.BAD_REQUEST,"Duracao deve estar entre 1 e 720 minutos.");
        var inicio=m.getData().atTime(m.getHorario());
        if(!inicio.plusMinutes(duracao).toLocalDate().equals(m.getData())) throw new ApiException(HttpStatus.BAD_REQUEST,"A sessao deve terminar no mesmo dia.");
        boolean mudou=anterior==null||!Objects.equals(m.getData(),anterior.getData())||!Objects.equals(m.getHorario(),anterior.getHorario())
            ||!Objects.equals(m.getDuracaoMinutos(),anterior.getDuracaoMinutos())||!Objects.equals(m.getProfissional().getId(),anterior.getProfissional().getId());
        if(anterior!=null&&!DisponibilidadeService.ativa(anterior)&&anterior.getStatus()!=m.getStatus()) throw new ApiException(HttpStatus.CONFLICT,"Marcacoes encerradas nao podem mudar de status.");
        if(!DisponibilidadeService.ativa(m)) return;
        if(mudou&&inicio.isBefore(LocalDateTime.now(clock))) throw new ApiException(HttpStatus.BAD_REQUEST,"Nao e permitido agendar no passado.");
        if(mudou||!inicio.isBefore(LocalDateTime.now(clock))) disponibilidade.validarReserva(m);
    }
    private void protegerRecebimento(Long id){var m=marcacoes.findByIdAndEstudioId(id,acesso.estudioId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Marcacao nao encontrada."));if(recebimentos.existsByMarcacaoIdAndEstudioId(id,acesso.estudioId())||m.getPresenca()!=null)throw new ApiException(HttpStatus.CONFLICT,"Atendimento com comparecimento registrado nao pode ser alterado ou excluido.");}
    private void informarRecebimento(Marcacao m,boolean confirmado){
        var zona=java.time.ZoneId.of(m.getEstudio().getFusoHorario());
        var fim=m.getData().atTime(m.getHorario()).plusMinutes(m.getDuracaoMinutos()==null?60:m.getDuracaoMinutos());
        var terminou=!fim.isAfter(LocalDateTime.now(clock.withZone(zona)));
        var permitido=!confirmado&&m.getStatus()!=StatusMarcacao.CANCELADA&&!"FALTOU".equals(m.getPresenca())&&terminou;
        m.informarRecebimento(confirmado,permitido);
    }
    public Marcacao aplicarAcaoEmail(long estudioId,long id,String acao,LocalDate data,LocalTime horario){
        guard.bloquear(estudioId);
        var m=marcacoes.findByIdAndEstudioId(id,estudioId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Agendamento nao encontrado."));
        var inicioAnterior=m.getData().atTime(m.getHorario());var statusAnterior=m.getStatus();
        if(statusAnterior==StatusMarcacao.CANCELADA)throw new ApiException(HttpStatus.CONFLICT,"Agendamento cancelado nao pode ser alterado.");
        switch(acao){
            case "CONFIRMAR" -> m.setStatus(StatusMarcacao.CONFIRMADA);
            case "CANCELAR" -> m.setStatus(StatusMarcacao.CANCELADA);
            case "REAGENDAR" -> {
                if(data==null||horario==null)throw new ApiException(HttpStatus.BAD_REQUEST,"Informe o novo dia e horario.");
                var zone=java.time.ZoneId.of(m.getEstudio().getFusoHorario());var now=LocalDateTime.now(clock.withZone(zone));var start=data.atTime(horario);
                if(data.isBefore(now.toLocalDate())||data.isAfter(now.toLocalDate().plusDays(365))||start.isBefore(now.plusMinutes(m.getEstudio().getAntecedenciaMinimaMinutos())))
                    throw new ApiException(HttpStatus.CONFLICT,"Escolha um horario futuro com a antecedencia necessaria.");
                if(!disponibilidade.consultarEstudio(estudioId,m.getProfissional().getId(),data,m.getDuracaoMinutos(),id).horarios().contains(horario))
                    throw new ApiException(HttpStatus.CONFLICT,"Esse horario nao esta mais disponivel.");
                m.setData(data);m.setHorario(horario);m.setStatus(StatusMarcacao.AGENDADA);
            }
            default -> throw new ApiException(HttpStatus.BAD_REQUEST,"Acao de agendamento invalida.");
        }
        Marcacao salva=marcacoes.saveAndFlush(m);agendamentoEmail.sincronizar(salva,inicioAnterior,statusAnterior);
        if(acao.equals("CANCELAR"))listaEspera.processarCancelamento(estudioId,salva.getId());
        return salva;
    }

    public void deletar(Long id){
        guard.bloquear();var m=buscarPorId(id);protegerRecebimento(id);
        var inicioAnterior=m.getData().atTime(m.getHorario());var statusAnterior=m.getStatus();
        if(statusAnterior!=StatusMarcacao.CANCELADA)m.setStatus(StatusMarcacao.CANCELADA);
        Marcacao salva=marcacoes.saveAndFlush(m);agendamentoEmail.sincronizar(salva,inicioAnterior,statusAnterior);
        if(statusAnterior!=StatusMarcacao.CANCELADA)listaEspera.processarCancelamento(salva.getEstudio().getId(),salva.getId());
    }
}

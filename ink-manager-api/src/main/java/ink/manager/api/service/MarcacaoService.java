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
    public MarcacaoService(MarcacaoRepository marcacoes,ClienteRepository clientes,EstudioRepository estudios,
            AgendaGuard guard,DisponibilidadeService disponibilidade,AcessoAtual acesso,Clock clock){
        this.marcacoes=marcacoes;this.clientes=clientes;this.estudios=estudios;this.guard=guard;
        this.disponibilidade=disponibilidade;this.acesso=acesso;this.clock=clock;
    }
    @Transactional(readOnly=true) public List<Marcacao> listarTodas(){return marcacoes.findByEstudioId(acesso.estudioId());}
    @Transactional(readOnly=true) public Marcacao buscarPorId(Long id){
        return marcacoes.findByIdAndEstudioId(id,acesso.estudioId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Marcacao nao encontrada."));
    }
    public Marcacao salvar(Marcacao marcacao){
        guard.bloquear();marcacao.setId(null);marcacao.setVersion(null);resolverVinculos(marcacao);validarAgenda(marcacao,null);return marcacoes.saveAndFlush(marcacao);
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
        validarAgenda(marcacao,null);return marcacoes.saveAndFlush(marcacao);
    }
    public Marcacao atualizar(Long id,Marcacao marcacao){guard.bloquear();return atualizarInterno(buscarPorId(id),marcacao);}
    private Marcacao atualizarInterno(Marcacao atual,Marcacao marcacao){
        if(marcacao.getVersion()==null||!Objects.equals(atual.getVersion(),marcacao.getVersion())) throw new ApiException(HttpStatus.CONFLICT,"Registro alterado. Recarregue antes de salvar.");
        marcacao.setId(atual.getId());resolverVinculos(marcacao);validarAgenda(marcacao,atual);
        atual.setCliente(marcacao.getCliente());atual.setProfissional(marcacao.getProfissional());atual.setData(marcacao.getData());
        atual.setHorario(marcacao.getHorario());atual.setDescricao(marcacao.getDescricao());atual.setStatus(marcacao.getStatus());atual.setDuracaoMinutos(marcacao.getDuracaoMinutos());
        return marcacoes.saveAndFlush(atual);
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
    public void deletar(Long id){guard.bloquear();marcacoes.delete(buscarPorId(id));}
}

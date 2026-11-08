package ink.manager.api.service;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import ink.manager.api.config.AcessoAtual;
import ink.manager.api.dto.*;
import ink.manager.api.model.Profissional;
import ink.manager.api.model.Servico;
import ink.manager.api.repository.*;
import ink.manager.api.exception.ApiException;

@Service @Transactional
public class ServicoService {
    private final ServicoRepository servicos;
    private final ProfissionalRepository profissionais;
    private final ServicoProfissionalRepository vinculos;
    private final EstudioRepository estudios;
    private final AcessoAtual acesso;
    private final AgendaGuard guard;
    public ServicoService(ServicoRepository servicos,ProfissionalRepository profissionais,ServicoProfissionalRepository vinculos,
            EstudioRepository estudios,AcessoAtual acesso,AgendaGuard guard){
        this.servicos=servicos;this.profissionais=profissionais;this.vinculos=vinculos;this.estudios=estudios;this.acesso=acesso;this.guard=guard;
    }
    @Transactional(readOnly=true) public List<ServicoResponse> listar(){return servicos.findByEstudioIdOrderByNomeAsc(acesso.estudioId()).stream().map(this::resposta).toList();}
    @PreAuthorize("hasRole('PROPRIETARIO')")
    public ServicoResponse salvar(Long id,ServicoRequest r){
        guard.bloquear();
        Long estudioId=acesso.estudioId();boolean novo=id==null;
        var s=id==null?new Servico():servicos.findByIdAndEstudioId(id,acesso.estudioId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Servico nao encontrado."));
        if(id!=null&&!Objects.equals(s.getVersion(),r.version()))throw new ApiException(HttpStatus.CONFLICT,"Servico alterado. Recarregue antes de salvar.");
        s.setEstudio(estudios.getReferenceById(estudioId));s.setNome(r.nome().trim());s.setPreco(r.preco());s.setDuracaoMinutos(r.duracaoMinutos());s.setAtivo(r.ativo());
        s=servicos.saveAndFlush(s);
        if(r.profissionalIds()!=null) substituirProfissionais(s,estudioId,r.profissionalIds());
        else if(novo) substituirProfissionais(s,estudioId,profissionais.findByEstudioId(estudioId).stream().map(Profissional::getId).toList());
        return resposta(s);
    }
    private ServicoResponse resposta(Servico s){return ServicoResponse.from(s,vinculos.listarProfissionalIds(acesso.estudioId(),s.getId()));}
    private void substituirProfissionais(Servico servico,Long estudioId,List<Long> solicitados){
        var ids=new LinkedHashSet<Long>();
        for(Long id:solicitados){
            if(id==null||id<=0)throw new ApiException(HttpStatus.BAD_REQUEST,"Informe ids de profissionais validos.");
            if(!ids.add(id))throw new ApiException(HttpStatus.BAD_REQUEST,"Profissional duplicado na lista do servico.");
        }
        var selecionados=new ArrayList<Profissional>();
        for(Long id:ids)selecionados.add(profissionais.findByIdAndEstudioId(id,estudioId)
                .orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Profissional nao encontrado neste estudio.")));
        vinculos.removerVinculos(estudioId,servico.getId());
        if(!selecionados.isEmpty())vinculos.saveAll(selecionados.stream()
                .map(profissional->new ink.manager.api.model.ServicoProfissional(estudioId,servico,profissional)).toList());
    }
    @Transactional(readOnly=true) @PreAuthorize("hasRole('PROPRIETARIO')")
    public Map<String,String> link(){var e=estudios.findById(acesso.estudioId()).orElseThrow();return Map.of(
        "caminho","/agendar.html?local="+e.getLinkPublico(),
        "caminhoCurto","/a/"+PublicLinkCode.encode(e.getLinkPublico()));}
}

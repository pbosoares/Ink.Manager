package ink.manager.api.service;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import ink.manager.api.config.AcessoAtual;
import ink.manager.api.dto.*;
import ink.manager.api.model.Servico;
import ink.manager.api.repository.*;
import ink.manager.api.exception.ApiException;

@Service @Transactional
public class ServicoService {
    private final ServicoRepository servicos;
    private final EstudioRepository estudios;
    private final AcessoAtual acesso;
    private final AgendaGuard guard;
    public ServicoService(ServicoRepository servicos,EstudioRepository estudios,AcessoAtual acesso,AgendaGuard guard){this.servicos=servicos;this.estudios=estudios;this.acesso=acesso;this.guard=guard;}
    @Transactional(readOnly=true) public List<ServicoResponse> listar(){return servicos.findByEstudioIdOrderByNomeAsc(acesso.estudioId()).stream().map(ServicoResponse::from).toList();}
    @PreAuthorize("hasRole('PROPRIETARIO')")
    public ServicoResponse salvar(Long id,ServicoRequest r){
        guard.bloquear();
        var s=id==null?new Servico():servicos.findByIdAndEstudioId(id,acesso.estudioId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Servico nao encontrado."));
        if(id!=null&&!Objects.equals(s.getVersion(),r.version()))throw new ApiException(HttpStatus.CONFLICT,"Servico alterado. Recarregue antes de salvar.");
        s.setEstudio(estudios.getReferenceById(acesso.estudioId()));s.setNome(r.nome().trim());s.setPreco(r.preco());s.setDuracaoMinutos(r.duracaoMinutos());s.setAtivo(r.ativo());
        return ServicoResponse.from(servicos.saveAndFlush(s));
    }
    @Transactional(readOnly=true) @PreAuthorize("hasRole('PROPRIETARIO')")
    public Map<String,String> link(){var e=estudios.findById(acesso.estudioId()).orElseThrow();return Map.of("caminho","/agendar.html?local="+e.getLinkPublico());}
}

package ink.manager.api.service;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ink.manager.api.config.AcessoAtual;
import ink.manager.api.dto.RegistroFalta;
import ink.manager.api.exception.ApiException;
import ink.manager.api.repository.ClienteRepository;
import ink.manager.api.repository.MarcacaoRepository;
@Service @Transactional(readOnly=true) @PreAuthorize("hasRole('PROPRIETARIO')")
public class HistoricoFaltasService {
    private final ClienteRepository clientes;private final MarcacaoRepository marcacoes;private final AcessoAtual acesso;
    public HistoricoFaltasService(ClienteRepository clientes,MarcacaoRepository marcacoes,AcessoAtual acesso){this.clientes=clientes;this.marcacoes=marcacoes;this.acesso=acesso;}
    public List<RegistroFalta> porCliente(Long id){long estudio=acesso.estudioId();var cliente=clientes.findByIdAndEstudioId(id,estudio).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Cliente nao encontrado."));String email=cliente.getEmail();return email==null||email.isBlank()?marcacoes.historicoFaltas(estudio,id):marcacoes.historicoFaltasEmail(estudio,email.toLowerCase(java.util.Locale.ROOT));}
}

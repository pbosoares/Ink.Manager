package ink.manager.api.service;
import ink.manager.api.config.AcessoAtual;
import ink.manager.api.exception.ApiException;
import ink.manager.api.repository.EstudioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
@Component
public class AgendaGuard {
    private final EstudioRepository estudios;
    private final AcessoAtual acesso;
    public AgendaGuard(EstudioRepository estudios, AcessoAtual acesso) { this.estudios = estudios; this.acesso = acesso; }
    public void bloquear() { bloquear(acesso.estudioId()); }
    public void bloquear(Long estudioId) {
        var estudio = estudios.bloquear(estudioId).orElseThrow();
        if (estudio.isEncerrado())
            throw new ApiException(HttpStatus.GONE, "A conta deste estabelecimento foi encerrada.");
    }
}

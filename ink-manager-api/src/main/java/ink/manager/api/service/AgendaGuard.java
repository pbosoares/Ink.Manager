package ink.manager.api.service;
import ink.manager.api.config.AcessoAtual;
import ink.manager.api.repository.EstudioRepository;
import org.springframework.stereotype.Component;
@Component
public class AgendaGuard {
    private final EstudioRepository estudios;
    private final AcessoAtual acesso;
    public AgendaGuard(EstudioRepository estudios, AcessoAtual acesso) { this.estudios = estudios; this.acesso = acesso; }
    public void bloquear() { estudios.bloquear(acesso.estudioId()).orElseThrow(); }
}

package ink.manager.api.service;

import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ink.manager.api.config.AcessoAtual;
import ink.manager.api.exception.ApiException;
import ink.manager.api.model.Estudio;
import ink.manager.api.repository.EstudioRepository;
import java.time.ZoneId;

@Service
@Transactional
public class ConfiguracaoAgendamentoService {
    public record Configuracao(@NotNull @Min(0) @Max(43200) Integer antecedenciaMinimaMinutos,
            @NotNull @Min(1) @Max(50) Integer limiteAgendamentosPorEmail,
            @NotBlank @Size(max=64) String fusoHorario) {}
    private final EstudioRepository estudios;
    private final AgendaGuard guard;
    private final AcessoAtual acesso;
    public ConfiguracaoAgendamentoService(EstudioRepository estudios,AgendaGuard guard,AcessoAtual acesso){
        this.estudios=estudios;this.guard=guard;this.acesso=acesso;
    }
    public Configuracao obter(){return resposta(estudios.findById(acesso.estudioId()).orElseThrow());}
    public Configuracao atualizar(Configuracao c){
        guard.bloquear();
        try{ZoneId.of(c.fusoHorario());}catch(RuntimeException erro){throw new ApiException(HttpStatus.BAD_REQUEST,"Escolha um fuso horario valido.");}
        Estudio e=estudios.findById(acesso.estudioId()).orElseThrow();
        e.setAntecedenciaMinimaMinutos(c.antecedenciaMinimaMinutos());
        e.setLimiteAgendamentosEmail(c.limiteAgendamentosPorEmail());
        e.setFusoHorario(c.fusoHorario());
        estudios.saveAndFlush(e);
        return resposta(e);
    }
    private Configuracao resposta(Estudio e){return new Configuracao(e.getAntecedenciaMinimaMinutos(),e.getLimiteAgendamentosEmail(),e.getFusoHorario());}
}

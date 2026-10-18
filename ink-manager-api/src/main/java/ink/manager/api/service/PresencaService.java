package ink.manager.api.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ink.manager.api.config.AcessoAtual;
import ink.manager.api.exception.ApiException;
import ink.manager.api.model.Marcacao;
import ink.manager.api.model.StatusMarcacao;
import ink.manager.api.repository.MarcacaoRepository;
import ink.manager.api.repository.RecebimentoRepository;
import java.time.*;

@Service @Transactional @PreAuthorize("hasRole('PROPRIETARIO')")
public class PresencaService {
    public record Pedido(@NotBlank @Pattern(regexp="COMPARECEU|FALTOU") String presenca){}
    private final MarcacaoRepository marcacoes;
    private final RecebimentoRepository recebimentos;
    private final AgendaGuard guard;
    private final AcessoAtual acesso;
    private final Clock clock;
    public PresencaService(MarcacaoRepository marcacoes,RecebimentoRepository recebimentos,AgendaGuard guard,AcessoAtual acesso,Clock clock){this.marcacoes=marcacoes;this.recebimentos=recebimentos;this.guard=guard;this.acesso=acesso;this.clock=clock;}
    public Marcacao atualizar(Long id,Pedido pedido){
        guard.bloquear();
        Marcacao m=marcacoes.findByIdAndEstudioId(id,acesso.estudioId()).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Agendamento nao encontrado."));
        if(m.getStatus()==StatusMarcacao.CANCELADA)throw new ApiException(HttpStatus.CONFLICT,"Agendamento cancelado nao tem presenca.");
        if(m.getStatus()==StatusMarcacao.CONCLUIDA&&"FALTOU".equals(pedido.presenca()))throw new ApiException(HttpStatus.CONFLICT,"Atendimento concluido nao pode ser marcado como falta.");
        if("FALTOU".equals(pedido.presenca())&&recebimentos.existsByMarcacaoIdAndEstudioId(id,acesso.estudioId()))throw new ApiException(HttpStatus.CONFLICT,"Atendimento com recebimento confirmado nao pode virar falta.");
        var zona=ZoneId.of(m.getEstudio().getFusoHorario());
        LocalDateTime fim=m.getData().atTime(m.getHorario()).plusMinutes(m.getDuracaoMinutos());
        if(fim.isAfter(LocalDateTime.now(clock.withZone(zona))))throw new ApiException(HttpStatus.CONFLICT,"Registre comparecimento ou falta depois do horario do atendimento.");
        m.setPresenca(pedido.presenca());
        return marcacoes.saveAndFlush(m);
    }
}

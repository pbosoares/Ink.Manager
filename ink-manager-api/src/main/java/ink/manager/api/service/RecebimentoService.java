package ink.manager.api.service;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import ink.manager.api.config.AcessoAtual;
import ink.manager.api.exception.ApiException;
import ink.manager.api.model.*;
import ink.manager.api.repository.*;
import java.time.ZoneId;

@Service @Transactional @PreAuthorize("hasRole('PROPRIETARIO')")
public class RecebimentoService {
    public record Confirmar(@NotNull @DecimalMin("0.00") @Digits(integer=10,fraction=2) BigDecimal valor){}
    public record Linha(Long id,Long marcacaoId,String cliente,String servico,BigDecimal valor,OffsetDateTime confirmadoEm){}
    public record Resumo(String moeda,String fusoHorario,LocalDate inicioSemana,LocalDate fimSemana,
        LocalDate inicioMes,LocalDate fimMes,BigDecimal totalSemana,BigDecimal totalMes,List<Linha> recebimentos){}
    private final RecebimentoRepository recebimentos;
    private final MarcacaoRepository marcacoes;
    private final AgendaGuard guard;
    private final AcessoAtual acesso;
    private final Clock clock;
    private final ink.manager.api.repository.EstudioRepository estudios;
    public RecebimentoService(RecebimentoRepository recebimentos,MarcacaoRepository marcacoes,AgendaGuard guard,AcessoAtual acesso,Clock clock,ink.manager.api.repository.EstudioRepository estudios){this.recebimentos=recebimentos;this.marcacoes=marcacoes;this.guard=guard;this.acesso=acesso;this.clock=clock;this.estudios=estudios;}
    private Linha linha(Recebimento r){var m=r.getMarcacao();return new Linha(r.getId(),m.getId(),m.getCliente().getNome(),m.getServicoNome()==null?m.getDescricao():m.getServicoNome(),r.getValor(),r.getConfirmadoEm().atZone(ZoneId.of(m.getEstudio().getFusoHorario())).toOffsetDateTime());}
    public Linha confirmar(Long marcacaoId,Confirmar pedido){
        guard.bloquear();long estudio=acesso.estudioId();
        var m=marcacoes.findByIdAndEstudioId(marcacaoId,estudio).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Agendamento nao encontrado."));
        var anterior=recebimentos.findByMarcacaoIdAndEstudioId(marcacaoId,estudio);
        if(anterior.isPresent()){
            if(anterior.get().getValor().compareTo(pedido.valor())!=0)throw new ApiException(HttpStatus.CONFLICT,"Recebimento ja confirmado com outro valor.");
            return linha(anterior.get());
        }
        if(m.getStatus()==StatusMarcacao.CANCELADA)throw new ApiException(HttpStatus.CONFLICT,"Agendamento cancelado nao pode ter recebimento.");
        if("FALTOU".equals(m.getPresenca()))throw new ApiException(HttpStatus.CONFLICT,"Agendamento marcado como falta nao pode receber confirmacao de valor.");
        var fim=m.getData().atTime(m.getHorario()).plusMinutes(m.getDuracaoMinutos()==null?60:m.getDuracaoMinutos());
        if(m.getStatus()!=StatusMarcacao.CONCLUIDA&&fim.isAfter(LocalDateTime.now(clock.withZone(ZoneId.of(m.getEstudio().getFusoHorario())))))throw new ApiException(HttpStatus.CONFLICT,"Conclua o atendimento e confirme o recebimento somente depois do horario previsto para o termino.");
        m.setStatus(StatusMarcacao.CONCLUIDA);m.setPresenca("COMPARECEU");marcacoes.saveAndFlush(m);
        // Match TIMESTAMP(6) before responding, so a retry returns the exact same instant.
        var instante=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        return linha(recebimentos.saveAndFlush(new Recebimento(m,pedido.valor().setScale(2),instante,acesso.usuarioId())));
    }
    @Transactional(readOnly=true) public Resumo resumo(LocalDate referencia){
        ZoneId zona=ZoneId.of(estudios.findById(acesso.estudioId()).orElseThrow().getFusoHorario());
        LocalDate dia=referencia==null?LocalDate.now(clock.withZone(zona)):referencia;
        LocalDate semana=dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),fimSemana=semana.plusDays(7),mes=dia.withDayOfMonth(1),fimMes=mes.plusMonths(1);
        LocalDate inicio=semana.isBefore(mes)?semana:mes,fim=fimSemana.isAfter(fimMes)?fimSemana:fimMes;
        var lista=recebimentos.findByEstudioIdAndConfirmadoEmGreaterThanEqualAndConfirmadoEmLessThanOrderByConfirmadoEmDesc(acesso.estudioId(),inicio.atStartOfDay(zona).toInstant(),fim.atStartOfDay(zona).toInstant());
        BigDecimal totalSemana=BigDecimal.ZERO.setScale(2),totalMes=BigDecimal.ZERO.setScale(2);
        for(var r:lista){var data=r.getConfirmadoEm().atZone(zona).toLocalDate();
            if(!data.isBefore(semana)&&data.isBefore(fimSemana))totalSemana=totalSemana.add(r.getValor());
            if(!data.isBefore(mes)&&data.isBefore(fimMes))totalMes=totalMes.add(r.getValor());
        }
        return new Resumo("BRL",zona.getId(),semana,fimSemana.minusDays(1),mes,fimMes.minusDays(1),totalSemana,totalMes,lista.stream().map(this::linha).toList());
    }
}

package ink.manager.api.service;

import ink.manager.api.model.Marcacao;
import ink.manager.api.model.StatusMarcacao;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgendamentoEmailService {
    private static final String CONFIRMACAO="CONFIRMACAO_AGENDAMENTO";
    private static final String LEMBRETE_24H="LEMBRETE_24H";
    private static final String LEMBRETE_2H="LEMBRETE_2H";
    private final EmailOutboxService outbox;
    private final java.time.Clock clock;

    public AgendamentoEmailService(EmailOutboxService outbox,java.time.Clock clock){this.outbox=outbox;this.clock=clock;}

    @Transactional(propagation=Propagation.MANDATORY)
    public void sincronizar(Marcacao atual,LocalDateTime inicioAnterior,StatusMarcacao statusAnterior){
        long estudio=atual.getEstudio().getId();String referencia=Long.toString(atual.getId());
        LocalDateTime inicio=atual.getData().atTime(atual.getHorario());
        boolean cancelou=atual.getStatus()==StatusMarcacao.CANCELADA;
        boolean mudouAgenda=inicioAnterior==null||!inicioAnterior.equals(inicio)
            ||statusAnterior==StatusMarcacao.CANCELADA&&!cancelou;
        if(mudouAgenda||cancelou){outbox.invalidate(estudio,CONFIRMACAO,referencia);outbox.invalidate(estudio,LEMBRETE_24H,referencia);outbox.invalidate(estudio,LEMBRETE_2H,referencia);}
        if(!mudouAgenda&&statusAnterior!=null&&!cancelou)return;
        if(cancelou||atual.getStatus()!=StatusMarcacao.AGENDADA&&atual.getStatus()!=StatusMarcacao.CONFIRMADA)return;
        String destinatario=atual.getCliente()==null?null:atual.getCliente().getEmail();
        if(destinatario==null||destinatario.isBlank())return;
        ZoneId zone=ZoneId.of(atual.getEstudio().getFusoHorario());Instant inicioInstant=inicio.atZone(zone).toInstant();
        enfileirar(estudio,atual,CONFIRMACAO,clock.instant(),true);
        enfileirar(estudio,atual,LEMBRETE_24H,inicioInstant.minus(Duration.ofHours(24)));
        enfileirar(estudio,atual,LEMBRETE_2H,inicioInstant.minus(Duration.ofHours(2)));
    }

    private void enfileirar(long estudio,Marcacao m,String tipo,Instant disponivel){
        enfileirar(estudio,m,tipo,disponivel,false);
    }

    private void enfileirar(long estudio,Marcacao m,String tipo,Instant disponivel,boolean imediato){
        if(!imediato&&!disponivel.isAfter(clock.instant()))return;
        String slot=m.getData()+"T"+m.getHorario()+"/"+m.getDuracaoMinutos();
        String json="{\"marcacaoId\":"+m.getId()+",\"inicio\":\""+slot+"\",\"duracaoMinutos\":"+m.getDuracaoMinutos()+"}";
        outbox.enqueue(estudio,m.getCliente().getEmail(),tipo,tipo+":"+m.getId()+":"+slot+":"+m.getVersion(),disponivel,Long.toString(m.getId()),json);
    }
}

package ink.manager.api.service;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import ink.manager.api.repository.EmailOutboxRepository;
import ink.manager.api.repository.EstudioRepository;

class EmailOutboxServiceTest {
    private final Instant now=Instant.parse("2026-10-20T12:00:00Z");
    private final EmailOutboxRepository repository=mock(EmailOutboxRepository.class);
    private final EmailOutboxService service=new EmailOutboxService(repository,mock(EstudioRepository.class),20,120,5,Clock.fixed(now,ZoneOffset.UTC));

    private EmailOutboxService.ClaimedEmail claimed(int attempt) {
        var job=new EmailOutboxJob(7,3,"cliente@example.com","LEMBRETE_24H","key",now,"7","{}");
        return new EmailOutboxService.ClaimedEmail(job,"lease-token",attempt);
    }

    @Test void falhaTransitoriaAgendaRetryComBackoffLimitadoESemDetalheSensivel() {
        var retryService=new EmailOutboxService(repository,mock(EstudioRepository.class),20,120,20,Clock.fixed(now,ZoneOffset.UTC));
        retryService.markFailure(claimed(19),now,"DELIVERY_FAILED",false);
        verify(repository).registrarFalha(7,"lease-token","PENDENTE",now.plusSeconds(15360),"DELIVERY_FAILED");
    }

    @Test void esgotaTentativasEUsaCodigoSeguro() {
        service.markFailure(claimed(5),now,"token-raw-should-not-persist",false);
        verify(repository).registrarFalha(7,"lease-token","FALHOU",now,"DELIVERY_FAILED");
    }

    @Test void flagDesligadaNaoConsultaSenderNemReivindicaOutbox() {
        var outbox=mock(EmailOutboxService.class);var sender=mock(EmailSender.class);
        var worker=new EmailOutboxWorker(outbox,List.of(),sender,Clock.fixed(now,ZoneOffset.UTC),false);
        worker.poll();
        verifyNoInteractions(outbox,sender);
    }
    @Test void workerInvalidaJobObsoletoSemRenderizarNemEnviar() {
        var outbox=mock(EmailOutboxService.class);var sender=mock(EmailSender.class);var renderer=mock(EmailOutboxRenderer.class);
        var job=new EmailOutboxJob(17,3,"cliente@example.com","LEMBRETE_24H","key",now,"17","{}");
        var claimed=new EmailOutboxService.ClaimedEmail(job,"lease-token",1);
        when(sender.disponivel()).thenReturn(true);when(outbox.claimReady(now)).thenReturn(List.of(claimed));
        when(renderer.suporta("LEMBRETE_24H")).thenReturn(true);when(renderer.obsoleto(job)).thenReturn(true);
        var worker=new EmailOutboxWorker(outbox,List.of(renderer),sender,Clock.fixed(now,ZoneOffset.UTC),true);
        worker.poll();
        verify(outbox).markInvalidated(claimed);verify(renderer,never()).render(job);verify(sender,never()).enviar(anyString(),any());
    }
}
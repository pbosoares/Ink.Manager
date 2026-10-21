package ink.manager.api.service;

import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EmailOutboxWorker {
    private final EmailOutboxService outbox;
    private final List<EmailOutboxRenderer> renderers;
    private final EmailSender sender;
    private final Clock clock;
    private final boolean enabled;

    public EmailOutboxWorker(EmailOutboxService outbox, List<EmailOutboxRenderer> renderers,
            EmailSender sender, Clock clock, @Value("${MAIL_ENABLED:false}") boolean enabled) {
        this.outbox = outbox;
        this.renderers = List.copyOf(renderers);
        this.sender = sender;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${mail.outbox.poll-interval-ms:10000}")
    public void poll() {
        if (!enabled || !sender.disponivel()) return;
        for (var claimed : outbox.claimReady(clock.instant())) deliver(claimed);
    }

    private void deliver(EmailOutboxService.ClaimedEmail claimed) {
        var matches = renderers.stream().filter(r -> r.suporta(claimed.job().tipo())).toList();
        if (matches.size() != 1) {
            fail(claimed, "RENDERER_UNAVAILABLE", true, matches);
            return;
        }
        try {
            if (matches.getFirst().obsoleto(claimed.job())) {
                outbox.markInvalidated(claimed);
                return;
            }
            var content = matches.getFirst().render(claimed.job());
            if (content == null || content.assunto() == null || content.assunto().isBlank()
                    || content.texto() == null || content.texto().isBlank()) {
                fail(claimed, "RENDERER_INVALID_CONTENT", true, matches);
                return;
            }
            sender.enviar(claimed.job().destinatario(), content);
            outbox.markSent(claimed, clock.instant());
        } catch (RuntimeException failure) {
            // Exception text can contain addresses, tokens or provider credentials.
            fail(claimed, "DELIVERY_FAILED", false, matches);
        }
    }

    private void fail(EmailOutboxService.ClaimedEmail claimed, String code, boolean permanent,
            List<EmailOutboxRenderer> matches) {
        if (!outbox.markFailure(claimed, clock.instant(), code, permanent)) return;
        for (EmailOutboxRenderer renderer : matches) {
            try { renderer.falhouDefinitivamente(claimed.job()); }
            catch (RuntimeException ignored) { /* Scheduled expiry remains the recovery path. */ }
        }
    }
}

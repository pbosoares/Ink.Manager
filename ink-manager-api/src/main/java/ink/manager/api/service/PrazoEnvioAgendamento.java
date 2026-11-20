package ink.manager.api.service;

import ink.manager.api.model.Marcacao;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Appointment e-mails are only useful close to their scheduled moment. Jobs keep accumulating
 * in the outbox while MAIL_ENABLED=false, so without this check enabling delivery would send a
 * backlog of stale confirmations and reminders for appointments that already happened.
 */
final class PrazoEnvioAgendamento {
    static final Duration ATRASO_MAXIMO_LEMBRETE = Duration.ofHours(1);
    static final Duration ATRASO_MAXIMO_CONFIRMACAO = Duration.ofHours(2);

    private PrazoEnvioAgendamento() {}

    /** True when the appointment already started or the job is later than {@code atrasoMaximo}. */
    static boolean expirado(EmailOutboxJob job, Marcacao marcacao, Clock clock, Duration atrasoMaximo) {
        Instant agora = clock.instant();
        Instant inicio = LocalDateTime.of(marcacao.getData(), marcacao.getHorario())
                .atZone(ZoneId.of(marcacao.getEstudio().getFusoHorario())).toInstant();
        return !agora.isBefore(inicio) || agora.isAfter(job.disponivelEm().plus(atrasoMaximo));
    }
}

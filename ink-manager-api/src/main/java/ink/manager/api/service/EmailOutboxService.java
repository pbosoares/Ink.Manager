package ink.manager.api.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ink.manager.api.model.EmailOutbox;
import ink.manager.api.repository.EmailOutboxRepository;
import ink.manager.api.repository.EstudioRepository;

@Service
public class EmailOutboxService {
    private static final Pattern RAW_TOKEN_FIELD = Pattern.compile(
        "(?i)\\\"(?:token|tokenraw|tokenbruto|rawtoken|accesstoken|resettoken)\\\"\\s*:");
    private final EmailOutboxRepository outbox;
    private final EstudioRepository estudios;
    private final int batchSize;
    private final long leaseSeconds;
    private final int maxAttempts;
    private final java.time.Clock clock;

    public EmailOutboxService(EmailOutboxRepository outbox, EstudioRepository estudios,
            @Value("${mail.outbox.batch-size:20}") int batchSize,
            @Value("${mail.outbox.lease-seconds:120}") long leaseSeconds,
            @Value("${mail.outbox.max-attempts:5}") int maxAttempts,
            java.time.Clock clock) {
        this.outbox = outbox;
        this.estudios = estudios;
        this.batchSize = Math.max(1, Math.min(batchSize, 100));
        this.leaseSeconds = Math.max(30, leaseSeconds);
        this.maxAttempts = Math.max(1, Math.min(maxAttempts, 20));
        this.clock = clock;
    }

    /** Must run inside the domain transaction; the unique key is scoped to the tenant. */
    @Transactional(propagation = Propagation.MANDATORY)
    public long enqueue(long estudioId, String destinatario, String tipo, String chaveIdempotencia,
            Instant disponivelEm, String referencia, String dadosJson) {
        if (estudioId <= 0 || destinatario == null || destinatario.isBlank() || destinatario.length() > 254
                || tipo == null || tipo.isBlank() || tipo.length() > 80
                || chaveIdempotencia == null || chaveIdempotencia.isBlank() || chaveIdempotencia.length() > 180
                || disponivelEm == null || referencia == null || referencia.isBlank() || referencia.length() > 255
                || dadosJson == null || dadosJson.length() > 8000 || RAW_TOKEN_FIELD.matcher(dadosJson).find()) {
            throw new IllegalArgumentException("Invalid email outbox request");
        }
        // Serialize a tenant's enqueue-by-key check, then rely on the DB unique constraint as final guard.
        var estudio = estudios.bloquear(estudioId).orElseThrow(() -> new IllegalArgumentException("Unknown tenant"));
        if (estudio.isEncerrado()) throw new IllegalStateException("Closed tenant cannot enqueue email");
        var existing = outbox.findByEstudioIdAndChaveIdempotencia(estudioId, chaveIdempotencia);
        if (existing.isPresent()) return existing.get().getId();
        return outbox.save(new EmailOutbox(estudioId, destinatario.trim(), tipo.trim(), chaveIdempotencia,
            disponivelEm, referencia, dadosJson, clock.instant())).getId();
    }

    @Transactional
    public List<ClaimedEmail> claimReady(Instant now) {
        var ids = outbox.encontrarProntos(now, PageRequest.of(0, batchSize));
        var claimed = new ArrayList<ClaimedEmail>();
        for (long id : ids) {
            var candidate = outbox.findById(id).orElse(null);
            if (candidate == null) continue;
            // Serialize claim with tenant closure so no new job can pass the close boundary.
            var estudio = estudios.bloquear(candidate.getEstudioId()).orElse(null);
            if (estudio == null || estudio.isEncerrado()) continue;
            String leaseToken = UUID.randomUUID().toString();
            if (outbox.reclamar(id, now, now.plusSeconds(leaseSeconds), leaseToken) != 1) continue;
            outbox.findById(id).ifPresent(e -> claimed.add(new ClaimedEmail(
                new EmailOutboxJob(e.getId(), e.getEstudioId(), e.getDestinatario(), e.getTipo(),
                    e.getChaveIdempotencia(), e.getDisponivelEm(), e.getReferencia(), e.getDadosJson()), leaseToken,
                e.getTentativas())));
        }
        return List.copyOf(claimed);
    }

    @Transactional
    public void markSent(ClaimedEmail claimed, Instant now) {
        outbox.marcarEnviado(claimed.job().id(), claimed.leaseToken(), now);
    }

    @Transactional
    public void markInvalidated(ClaimedEmail claimed) {
        outbox.invalidarReivindicado(claimed.job().id(), claimed.leaseToken());
    }

    public boolean estudioAberto(long estudioId) {
        return estudios.existsByIdAndEncerradoEmIsNull(estudioId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int invalidateForStudio(long estudioId) {
        if (estudioId <= 0) throw new IllegalArgumentException("Invalid tenant id");
        return outbox.invalidarDoEstudio(estudioId);
    }

    @Transactional
    public boolean markFailure(ClaimedEmail claimed, Instant now, String safeCode, boolean permanent) {
        boolean exhausted = claimed.attempt() >= maxAttempts;
        String state = permanent || exhausted ? "FALHOU" : "PENDENTE";
        Instant next = state.equals("FALHOU") ? now : now.plusSeconds(backoffSeconds(claimed.attempt()));
        int updated=outbox.registrarFalha(claimed.job().id(), claimed.leaseToken(), state, next, sanitize(safeCode));
        return updated==1&&state.equals("FALHOU");
    }

    /** Invalidates pending and leased messages; renderers must still revalidate domain state before sending. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int invalidate(long estudioId, String tipo, String referencia) {
        if (estudioId <= 0 || tipo == null || tipo.isBlank() || referencia == null || referencia.isBlank())
            throw new IllegalArgumentException("Invalid outbox invalidation request");
        return outbox.invalidar(estudioId, tipo, referencia);
    }

    private long backoffSeconds(int attempt) {
        int exponent = Math.max(0, Math.min(attempt - 1, 9));
        return Math.min(21600L, 30L << exponent);
    }

    private String sanitize(String code) {
        if (code == null || !code.matches("[A-Z0-9_]{1,60}")) return "DELIVERY_FAILED";
        return code;
    }

    public record ClaimedEmail(EmailOutboxJob job, String leaseToken, int attempt) {}
}

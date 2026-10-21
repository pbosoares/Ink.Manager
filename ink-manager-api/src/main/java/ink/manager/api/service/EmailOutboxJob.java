package ink.manager.api.service;

import java.time.Instant;

public record EmailOutboxJob(long id, long estudioId, String destinatario, String tipo,
        String chaveIdempotencia, Instant disponivelEm, String referencia, String dadosJson) {}

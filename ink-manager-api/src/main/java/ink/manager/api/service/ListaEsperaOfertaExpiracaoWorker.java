package ink.manager.api.service;

import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ListaEsperaOfertaExpiracaoWorker {
    private final ListaEsperaService lista;
    private final Clock clock;

    public ListaEsperaOfertaExpiracaoWorker(ListaEsperaService lista, Clock clock) {
        this.lista = lista;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${waitlist.offers.expiry-poll-ms:60000}")
    public void poll() {
        lista.expirarOfertas(clock.instant());
    }
}
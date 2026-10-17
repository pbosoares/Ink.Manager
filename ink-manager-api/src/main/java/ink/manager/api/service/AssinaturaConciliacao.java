package ink.manager.api.service;
import java.time.*;
import org.slf4j.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
public class AssinaturaConciliacao {
    private static final Logger log=LoggerFactory.getLogger(AssinaturaConciliacao.class);
    private final AssinaturaStore store;private final AssinaturaService service;private final MercadoPagoGateway gateway;private final Clock clock;
    public AssinaturaConciliacao(AssinaturaStore store,AssinaturaService service,MercadoPagoGateway gateway,Clock clock){this.store=store;this.service=service;this.gateway=gateway;this.clock=clock;}
    @Scheduled(fixedDelayString="${billing.sync-delay-ms:600000}",initialDelayString="${billing.sync-initial-delay-ms:60000}")
    public void conciliar(){
        if(!gateway.disponivel())return;
        for(var checkout:store.paraConciliar(LocalDateTime.now(clock).minusDays(2))){
            try{service.sincronizarCheckout(checkout);}
            catch(RuntimeException error){log.warn("Falha na conciliacao de assinatura {}: {}",checkout.id(),error.getClass().getSimpleName());}
            finally{store.marcarConciliacao(checkout.id(),LocalDateTime.now(clock));}
        }
    }
}

package ink.manager.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Attempts reconciliation before opening tenant routes; the filter remains closed on failure. */
@Component
public class ManifestoRestauracaoStartup implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ManifestoRestauracaoStartup.class);
    private final ManifestoEncerramentoStore manifesto;
    private final EncerramentoEstudioService encerramento;

    public ManifestoRestauracaoStartup(ManifestoEncerramentoStore manifesto,
            EncerramentoEstudioService encerramento) {
        this.manifesto = manifesto;
        this.encerramento = encerramento;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!manifesto.exigeReconciliacaoNaInicializacao()) return;
        try {
            encerramento.reconciliarManifestosNaInicializacao();
        } catch (RuntimeException failure) {
            log.error("External closure manifest is not reconciled; tenant traffic remains blocked.");
        }
    }
}

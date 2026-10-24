package ink.manager.api.service;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/** Keeps tenant traffic blocked until an external closure ledger has been replayed. */
@Component
public class ManifestoRestauracaoGate {
    private final boolean required;
    private final AtomicBoolean open;

    public ManifestoRestauracaoGate(ManifestoEncerramentoStore manifesto) {
        this.required = manifesto.exigeReconciliacaoNaInicializacao();
        this.open = new AtomicBoolean(!required);
    }

    public boolean bloqueado() {
        return required && !open.get();
    }

    public void fecharAteReconciliacao() {
        if (required) open.set(false);
    }

    public void liberarAposReconciliacao() {
        if (required) open.set(true);
    }
}

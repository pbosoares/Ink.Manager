package ink.manager.api.controller;

import ink.manager.api.service.EncerramentoEstudioService;
import ink.manager.api.service.ManifestoEncerramentoStore;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Manual pre-traffic restore gate for replaying signed tombstones into a restored database. */
@RestController
@RequestMapping("/internal/retencoes-financeiras/manifesto")
@PreAuthorize("hasRole('RETENTION_OPERATOR')")
public class ManifestoEncerramentoController {
    private final EncerramentoEstudioService service;
    private final ManifestoEncerramentoStore manifesto;

    public ManifestoEncerramentoController(EncerramentoEstudioService service, ManifestoEncerramentoStore manifesto) {
        this.service = service;
        this.manifesto = manifesto;
    }

    public record Inicializacao(boolean inicializado) {}

    @PostMapping("/inicializar")
    public Inicializacao inicializar() {
        return new Inicializacao(manifesto.inicializarGitHub());
    }

    @PostMapping("/reconciliar")
    public EncerramentoEstudioService.ResultadoManifesto reconciliar() {
        return service.reconciliarManifestos();
    }
}

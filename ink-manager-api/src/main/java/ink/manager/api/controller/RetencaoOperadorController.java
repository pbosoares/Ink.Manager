package ink.manager.api.controller;

import ink.manager.api.service.RetencaoFinanceiraService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Internal operations for reviewing holds after the studio owner has closed the account. */
@RestController
@RequestMapping("/internal/retencoes-financeiras/estudios/{estudioId}")
@PreAuthorize("hasRole('RETENTION_OPERATOR')")
public class RetencaoOperadorController {
    private final RetencaoFinanceiraService service;

    public RetencaoOperadorController(RetencaoFinanceiraService service) {
        this.service = service;
    }

    @GetMapping
    public List<RetencaoFinanceiraService.Hold> listar(@PathVariable long estudioId) {
        return service.listarOperador(estudioId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RetencaoFinanceiraService.Hold criar(@PathVariable long estudioId,
            @Valid @RequestBody RetencaoFinanceiraService.CriarPedido pedido) {
        return service.criarOperador(estudioId, pedido);
    }

    @PostMapping("/{holdId}/revisao")
    public RetencaoFinanceiraService.Hold revisar(@PathVariable long estudioId, @PathVariable long holdId,
            @Valid @RequestBody RetencaoFinanceiraService.RevisarPedido pedido) {
        return service.revisarOperador(estudioId, holdId, pedido);
    }

    @PostMapping("/{holdId}/encerramento")
    public RetencaoFinanceiraService.Hold encerrar(@PathVariable long estudioId, @PathVariable long holdId,
            @Valid @RequestBody RetencaoFinanceiraService.EncerrarPedido pedido) {
        return service.encerrarOperador(estudioId, holdId, pedido);
    }

    @GetMapping("/{holdId}/historico")
    public List<RetencaoFinanceiraService.Evento> historico(
            @PathVariable long estudioId, @PathVariable long holdId) {
        return service.historicoOperador(estudioId, holdId);
    }
}

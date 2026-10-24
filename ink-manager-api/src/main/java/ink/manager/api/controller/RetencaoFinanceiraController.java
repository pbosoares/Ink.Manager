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

@RestController
@RequestMapping("/estudio/retencoes-financeiras")
@PreAuthorize("hasRole('PROPRIETARIO')")
public class RetencaoFinanceiraController {
    private final RetencaoFinanceiraService service;

    public RetencaoFinanceiraController(RetencaoFinanceiraService service) {
        this.service = service;
    }

    @GetMapping
    public List<RetencaoFinanceiraService.Hold> listar() {
        return service.listar();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RetencaoFinanceiraService.Hold criar(
            @Valid @RequestBody RetencaoFinanceiraService.CriarPedido pedido) {
        return service.criar(pedido);
    }

    @PostMapping("/{id}/revisao")
    public RetencaoFinanceiraService.Hold revisar(@PathVariable long id,
            @Valid @RequestBody RetencaoFinanceiraService.RevisarPedido pedido) {
        return service.revisar(id, pedido);
    }

    @PostMapping("/{id}/encerramento")
    public RetencaoFinanceiraService.Hold encerrar(@PathVariable long id,
            @Valid @RequestBody RetencaoFinanceiraService.EncerrarPedido pedido) {
        return service.encerrar(id, pedido);
    }

    @GetMapping("/{id}/historico")
    public List<RetencaoFinanceiraService.Evento> historico(@PathVariable long id) {
        return service.historico(id);
    }
}

package ink.manager.api.controller;

import ink.manager.api.service.ListaEsperaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class ListaEsperaController {
    public record TokenOferta(@NotBlank @Size(min=32,max=128) String token) {}
    private final ListaEsperaService service;

    public ListaEsperaController(ListaEsperaService service) { this.service = service; }

    @PostMapping("/publico/{link}/lista-espera")
    public ListaEsperaService.EntradaCriada inscrever(@PathVariable String link,
            @Valid @RequestBody ListaEsperaService.Pedido pedido) {
        return service.inscrever(link, pedido);
    }

    @PostMapping("/publico/{link}/oferta/visualizar")
    public ListaEsperaService.OfertaPublica consultarOferta(@PathVariable String link, @Valid @RequestBody TokenOferta pedido) {
        return service.consultarOferta(link, pedido.token());
    }

    @PostMapping("/publico/{link}/oferta/aceitar")
    public ListaEsperaService.ReservaAceita aceitarOferta(@PathVariable String link, @Valid @RequestBody TokenOferta pedido) {
        return service.aceitarOferta(link, pedido.token());
    }

    @GetMapping("/lista-espera")
    @PreAuthorize("hasRole('PROPRIETARIO')")
    public List<ListaEsperaService.EntradaDono> listarDoDono() { return service.listarDoDono(); }

    @PostMapping("/lista-espera/{id}/cancelar")
    @PreAuthorize("hasRole('PROPRIETARIO')")
    public void cancelar(@PathVariable long id) { service.cancelarDoDono(id); }
}

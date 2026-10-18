package ink.manager.api.controller;
import java.time.LocalDate;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import ink.manager.api.service.*;
@RestController @RequestMapping("/publico/{link}")
public class AgendamentoPublicoController {
    private final AgendamentoPublicoService service;
    public AgendamentoPublicoController(AgendamentoPublicoService service){this.service=service;}
    @GetMapping public ResponseEntity<AgendamentoPublicoService.Catalogo> catalogo(@PathVariable String link){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.catalogo(link));}
    @GetMapping("/horarios") public ResponseEntity<DisponibilidadeService.HorariosDisponiveis> horarios(@PathVariable String link,@RequestParam Long servicoId,@RequestParam Long profissionalId,@RequestParam LocalDate data){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.horarios(link,servicoId,profissionalId,data));}
    @PostMapping("/reservas") public ResponseEntity<AgendamentoPublicoService.Confirmacao> reservar(@PathVariable String link,@Valid @RequestBody AgendamentoPublicoService.Pedido pedido){return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(service.reservar(link,pedido));}
}

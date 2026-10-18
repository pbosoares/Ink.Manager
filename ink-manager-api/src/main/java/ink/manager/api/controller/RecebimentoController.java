package ink.manager.api.controller;
import java.time.LocalDate;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import ink.manager.api.service.RecebimentoService;
@RestController @RequestMapping("/recebimentos")
public class RecebimentoController {
    private final RecebimentoService service;
    public RecebimentoController(RecebimentoService service){this.service=service;}
    @PostMapping("/marcacoes/{id}") public RecebimentoService.Linha confirmar(@PathVariable Long id,@Valid @RequestBody RecebimentoService.Confirmar r){return service.confirmar(id,r);}
    @GetMapping public ResponseEntity<RecebimentoService.Resumo> resumo(@RequestParam(required=false) LocalDate data){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.resumo(data));}
}

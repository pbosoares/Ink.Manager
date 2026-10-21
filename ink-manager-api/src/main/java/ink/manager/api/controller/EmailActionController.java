package ink.manager.api.controller;

import ink.manager.api.service.EmailActionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/publico/acoes")
public class EmailActionController {
    public record Visualizacao(@NotBlank @Size(min=32,max=128) String token) {}
    private final EmailActionService service;
    public EmailActionController(EmailActionService service){this.service=service;}
    @PostMapping("/visualizar") public EmailActionService.Previa visualizar(@Valid @RequestBody Visualizacao pedido){return service.visualizar(pedido.token());}
    @PostMapping public EmailActionService.Resultado executar(@Valid @RequestBody EmailActionService.Pedido pedido){return service.executar(pedido);}
}

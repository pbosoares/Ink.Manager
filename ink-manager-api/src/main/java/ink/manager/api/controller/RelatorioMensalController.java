package ink.manager.api.controller;

import java.time.YearMonth;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ink.manager.api.service.RelatorioMensalService;

@RestController @RequestMapping("/relatorios") @PreAuthorize("hasRole('PROPRIETARIO')")
public class RelatorioMensalController {
    private final RelatorioMensalService service;
    public RelatorioMensalController(RelatorioMensalService service){this.service=service;}
    @GetMapping("/mensal") public RelatorioMensalService.Relatorio mensal(@RequestParam(required=false) YearMonth mes){return service.obter(mes);}
}

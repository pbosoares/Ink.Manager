package ink.manager.api.controller;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ink.manager.api.service.ConfiguracaoAgendamentoService;

@RestController
@RequestMapping("/configuracao/agendamento")
@PreAuthorize("hasRole('PROPRIETARIO')")
public class ConfiguracaoAgendamentoController {
    private final ConfiguracaoAgendamentoService service;
    public ConfiguracaoAgendamentoController(ConfiguracaoAgendamentoService service){this.service=service;}
    @GetMapping public ConfiguracaoAgendamentoService.Configuracao obter(){return service.obter();}
    @PutMapping public ConfiguracaoAgendamentoService.Configuracao atualizar(@Valid @RequestBody ConfiguracaoAgendamentoService.Configuracao c){return service.atualizar(c);}
}

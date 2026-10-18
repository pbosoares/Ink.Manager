package ink.manager.api.controller;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import ink.manager.api.dto.*;
import ink.manager.api.service.ServicoService;
@RestController @RequestMapping("/servicos")
public class ServicoController {
    private final ServicoService service;
    public ServicoController(ServicoService service){this.service=service;}
    @GetMapping public List<ServicoResponse> listar(){return service.listar();}
    @GetMapping("/link") public Map<String,String> link(){return service.link();}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public ServicoResponse criar(@Valid @RequestBody ServicoRequest r){return service.salvar(null,r);}
    @PutMapping("/{id}") public ServicoResponse atualizar(@PathVariable Long id,@Valid @RequestBody ServicoRequest r){return service.salvar(id,r);}
}

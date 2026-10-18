package ink.manager.api.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ink.manager.api.model.Cliente;
import ink.manager.api.dto.*;
import ink.manager.api.service.ClienteService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/clientes")
public class ClienteController {

    private final ClienteService clienteService;
    private final ink.manager.api.service.HistoricoFaltasService faltas;

    public ClienteController(ClienteService clienteService,ink.manager.api.service.HistoricoFaltasService faltas) {
        this.clienteService = clienteService;
        this.faltas=faltas;
    }

    @GetMapping
    public List<ClienteResponse> listarTodos() {
        return clienteService.listarTodos().stream().map(ClienteResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteResponse> buscarPorId(@PathVariable Long id) {
        return clienteService.buscarPorId(id)
                .map(ClienteResponse::from).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/historico-faltas")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('PROPRIETARIO')")
    public List<ink.manager.api.dto.RegistroFalta> historicoFaltas(@PathVariable Long id){return faltas.porCliente(id);}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClienteResponse criar(@Valid @RequestBody ClienteRequest cliente) {
        return ClienteResponse.from(clienteService.salvar(cliente.toModel()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClienteResponse> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody ClienteRequest cliente) {

        Cliente atualizado = clienteService.atualizar(id, cliente.toModel());
        return ResponseEntity.ok(ClienteResponse.from(atualizado));
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('PROPRIETARIO')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        clienteService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}

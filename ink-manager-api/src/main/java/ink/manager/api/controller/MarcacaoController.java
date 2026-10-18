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

import ink.manager.api.model.Marcacao;
import ink.manager.api.dto.*;
import jakarta.validation.Valid;
import ink.manager.api.service.MarcacaoService;

@RestController
@RequestMapping("/marcacoes")
public class MarcacaoController {

    private final MarcacaoService marcacaoService;
    private final ink.manager.api.service.PresencaService presencaService;

    public MarcacaoController(MarcacaoService marcacaoService,ink.manager.api.service.PresencaService presencaService) {
        this.marcacaoService = marcacaoService;
        this.presencaService=presencaService;
    }

    @GetMapping
    public List<MarcacaoResponse> listarTodas() {
        return marcacaoService.listarTodas().stream().map(MarcacaoResponse::from).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<MarcacaoResponse> buscarPorId(@PathVariable Long id) {
        Marcacao marcacao = marcacaoService.buscarPorId(id);
        return ResponseEntity.ok(MarcacaoResponse.from(marcacao));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MarcacaoResponse criar(@Valid @RequestBody MarcacaoRequest marcacao) {
        return MarcacaoResponse.from(marcacaoService.salvar(marcacao.toModel()));
    }

    @PostMapping("/com-cliente")
    @ResponseStatus(HttpStatus.CREATED)
    public MarcacaoResponse criarComCliente(@Valid @RequestBody MarcacaoComClienteRequest request) {
        return MarcacaoResponse.from(marcacaoService.salvarComCliente(null, request));
    }

    @PutMapping("/{id}/com-cliente")
    public MarcacaoResponse atualizarComCliente(@PathVariable Long id,
            @Valid @RequestBody MarcacaoComClienteRequest request) {
        return MarcacaoResponse.from(marcacaoService.salvarComCliente(id, request));
    }

    @PutMapping("/{id}/presenca")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('PROPRIETARIO')")
    public MarcacaoResponse registrarPresenca(@PathVariable Long id,@Valid @RequestBody ink.manager.api.service.PresencaService.Pedido pedido){
        return MarcacaoResponse.from(presencaService.atualizar(id,pedido));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MarcacaoResponse> atualizar(
            @PathVariable Long id,
            @Valid @RequestBody MarcacaoRequest marcacao) {

        Marcacao atualizada = marcacaoService.atualizar(id, marcacao.toModel());
        return ResponseEntity.ok(MarcacaoResponse.from(atualizada));
    }

    @org.springframework.security.access.prepost.PreAuthorize("hasRole('PROPRIETARIO')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        marcacaoService.deletar(id);
        return ResponseEntity.noContent().build();
    }
}

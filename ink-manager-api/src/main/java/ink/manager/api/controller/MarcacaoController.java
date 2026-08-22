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
import ink.manager.api.service.MarcacaoService;

@RestController
@RequestMapping("/marcacoes")
public class MarcacaoController {

    private final MarcacaoService marcacaoService;

    public MarcacaoController(MarcacaoService marcacaoService) {
        this.marcacaoService = marcacaoService;
    }

    @GetMapping
    public List<Marcacao> listarTodas() {
        return marcacaoService.listarTodas();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Marcacao> buscarPorId(@PathVariable Long id) {
        try {
            Marcacao marcacao = marcacaoService.buscarPorId(id);
            return ResponseEntity.ok(marcacao);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Marcacao criar(@RequestBody Marcacao marcacao) {
        return marcacaoService.salvar(marcacao);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Marcacao> atualizar(
            @PathVariable Long id,
            @RequestBody Marcacao marcacao) {

        try {
            Marcacao atualizada = marcacaoService.atualizar(id, marcacao);
            return ResponseEntity.ok(atualizada);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        try {
            marcacaoService.deletar(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
package ink.manager.api.controller;
import java.util.Objects;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import ink.manager.api.config.AcessoAtual;
import ink.manager.api.exception.ApiException;
import ink.manager.api.repository.EstudioRepository;
import ink.manager.api.service.EncerramentoEstudioService;
@RestController
@RequestMapping("/estudio")
public class EstudioController {
    public record Request(@NotBlank @Size(max = 100) String nome, @NotNull @PositiveOrZero Long version) {}
    public record Response(Long id, String nome, Long version) {}
    public record EncerramentoRequest(@NotBlank @Size(max = 20) String confirmacao) {}
    private final EstudioRepository estudios;
    private final AcessoAtual acesso;
    private final EncerramentoEstudioService encerramento;
    public EstudioController(EstudioRepository estudios, AcessoAtual acesso, EncerramentoEstudioService encerramento) {
        this.estudios = estudios; this.acesso = acesso; this.encerramento = encerramento;
    }
    @GetMapping
    public Response consultar() {
        var estudio = estudios.findById(acesso.estudioId()).orElseThrow();
        return new Response(estudio.getId(), estudio.getNome(), estudio.getVersion());
    }
    @PutMapping @Transactional @PreAuthorize("hasRole('PROPRIETARIO')")
    public Response atualizar(@Valid @RequestBody Request request) {
        var estudio = estudios.bloquear(acesso.estudioId()).orElseThrow();
        if (!Objects.equals(estudio.getVersion(), request.version())) throw new ApiException(HttpStatus.CONFLICT, "Estudio alterado. Recarregue a pagina.");
        estudio.setNome(request.nome().trim()); estudios.saveAndFlush(estudio);
        return new Response(estudio.getId(), estudio.getNome(), estudio.getVersion());
    }

    @PostMapping("/encerramento") @ResponseStatus(HttpStatus.ACCEPTED) @PreAuthorize("hasRole('PROPRIETARIO')")
    public EncerramentoEstudioService.Resultado encerrar(@Valid @RequestBody EncerramentoRequest request) {
        return encerramento.encerrar(request.confirmacao());
    }
}

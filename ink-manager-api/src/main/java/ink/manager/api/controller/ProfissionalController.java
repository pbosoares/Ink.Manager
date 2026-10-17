package ink.manager.api.controller;
import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ink.manager.api.dto.*;
import ink.manager.api.service.*;
@RestController @RequestMapping("/profissionais")
public class ProfissionalController {
    private final ProfissionalService profissionais;
    private final DisponibilidadeService disponibilidade;
    public ProfissionalController(ProfissionalService profissionais,DisponibilidadeService disponibilidade){this.profissionais=profissionais;this.disponibilidade=disponibilidade;}
    @GetMapping public List<ProfissionalResponse> listar(){return profissionais.listar();}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('PROPRIETARIO')")
    public ProfissionalResponse criar(@Valid @RequestBody ProfissionalRequest request){return profissionais.salvar(null,request);}
    @PutMapping("/{id}") @PreAuthorize("hasRole('PROPRIETARIO')")
    public ProfissionalResponse atualizar(@PathVariable Long id,@Valid @RequestBody ProfissionalRequest request){return profissionais.salvar(id,request);}
    @GetMapping("/{id}/disponibilidade")
    public DisponibilidadeService.HorariosDisponiveis horarios(@PathVariable Long id,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate data,
            @RequestParam(defaultValue="60") int duracaoMinutos,@RequestParam(required=false) Long ignorarMarcacaoId){return disponibilidade.consultar(id,data,duracaoMinutos,ignorarMarcacaoId);}
    @GetMapping("/{id}/dias-disponiveis")
    public List<DisponibilidadeService.DiaDisponivel> dias(@PathVariable Long id,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(defaultValue="31") int dias,@RequestParam(defaultValue="60") int duracaoMinutos){return disponibilidade.dias(id,inicio,dias,duracaoMinutos);}
    @GetMapping("/{id}/bloqueios") public List<BloqueioResponse> bloqueios(@PathVariable Long id){return profissionais.bloqueios(id);}
    @PostMapping("/{id}/bloqueios") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('PROPRIETARIO')")
    public BloqueioResponse bloquear(@PathVariable Long id,@Valid @RequestBody BloqueioRequest request){return profissionais.bloquearPeriodo(id,request);}
    @DeleteMapping("/{id}/bloqueios/{bloqueioId}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('PROPRIETARIO')")
    public void desbloquear(@PathVariable Long id,@PathVariable Long bloqueioId){profissionais.removerBloqueio(id,bloqueioId);}
}

package ink.manager.api.controller;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ink.manager.api.dto.*;
import ink.manager.api.service.UsuarioService;
@RestController @RequestMapping("/equipe") @PreAuthorize("hasRole('PROPRIETARIO')")
public class EquipeController {
    public record AcessoRequest(@NotNull Boolean ativo,@NotNull @PositiveOrZero Long version){}
    private final UsuarioService usuarios;
    public EquipeController(UsuarioService usuarios){this.usuarios=usuarios;}
    @GetMapping public List<UsuarioResponse> listar(){return usuarios.equipe();}
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse criar(@Valid @RequestBody NovoFuncionarioRequest request){return UsuarioResponse.from(usuarios.novoFuncionario(request));}
    @PutMapping("/{id}")
    public UsuarioResponse atualizar(@PathVariable Long id,@Valid @RequestBody AcessoRequest request){return usuarios.alterarAcesso(id,request.ativo(),request.version());}
    @PostMapping("/{id}/revogar-sessoes") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revogar(@PathVariable Long id){usuarios.revogar(id);}
}

package ink.manager.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ink.manager.api.dto.*;
import ink.manager.api.service.UsuarioService;

@RestController @RequestMapping("/auth")
public class AuthController {
    public record EstudioResumo(Long id,String nome,Long version){}
    public record Sessao(UsuarioResponse usuario,EstudioResumo estudio){}
    private final UsuarioService usuarios;
    public AuthController(UsuarioService usuarios){this.usuarios=usuarios;}
    @GetMapping("/config")
    public java.util.Map<String,Boolean> config(){return java.util.Map.of("registrationEnabled",true);}
    @PostMapping("/estudios") @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse cadastrar(@Valid @RequestBody NovoEstudioRequest request){return UsuarioResponse.from(usuarios.novoEstudio(request));}
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request){return new LoginResponse(usuarios.login(request.email(),request.senha()));}
    @GetMapping("/me")
    public Sessao me(){var usuario=usuarios.atual();var estudio=usuario.getEstudio();return new Sessao(UsuarioResponse.from(usuario),new EstudioResumo(estudio.getId(),estudio.getNome(),estudio.getVersion()));}
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(){usuarios.sair();}
}

package ink.manager.api.controller;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ink.manager.api.service.RecuperacaoSenhaService;
@RestController @RequestMapping("/auth")
public class RecuperacaoSenhaController {
    public record Solicitar(@NotBlank @Email @Size(max=255) String email){}
    public record Redefinir(@NotBlank @Pattern(regexp="[0-9a-f]{64}") String token,@NotBlank @Size(min=8,max=72) String senha){}
    private final RecuperacaoSenhaService service;
    public RecuperacaoSenhaController(RecuperacaoSenhaService service){this.service=service;}
    @PostMapping("/recuperar-senha")
    public Map<String,String> solicitar(@Valid @RequestBody Solicitar request){service.solicitar(request.email());return Map.of("mensagem","Se houver uma conta ativa, enviaremos as instrucoes por e-mail.");}
    @PostMapping("/redefinir-senha") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redefinir(@Valid @RequestBody Redefinir request){service.redefinir(request.token(),request.senha());}
}

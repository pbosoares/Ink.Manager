package ink.manager.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ink.manager.api.dto.LoginRequest;
import ink.manager.api.dto.LoginResponse;
import ink.manager.api.model.Usuario;
import ink.manager.api.service.UsuarioService;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UsuarioService usuarioService;

    public AuthController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping("/cadastro")
    public ResponseEntity<Usuario> cadastrar(@RequestBody Usuario usuario) {

        Usuario usuarioSalvo = usuarioService.cadastrar(usuario);

        return ResponseEntity.ok(usuarioSalvo);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest request) {

        String token = usuarioService.login(
            request.email(),
            request.senha()
        );

        return ResponseEntity.ok(new LoginResponse(token));
    }
}
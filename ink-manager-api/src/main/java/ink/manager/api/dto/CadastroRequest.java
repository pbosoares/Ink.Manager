package ink.manager.api.dto;
import jakarta.validation.constraints.*;
import ink.manager.api.model.Usuario;
public record CadastroRequest(
 @NotBlank @Size(max=100) String nome,
 @NotBlank @Email @Size(max=255) String email,
 @NotBlank @Size(min=8,max=72) String senha,
 @NotBlank @Size(max=256) String convite) {
 public Usuario toModel() { var u=new Usuario(); u.setNome(nome.trim()); u.setEmail(email.trim().toLowerCase(java.util.Locale.ROOT)); u.setSenha(senha); return u; }
}

package ink.manager.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "E-mail é obrigatório") @Email(message = "E-mail inválido") @jakarta.validation.constraints.Size(max=255) String email,
    @NotBlank(message = "Senha é obrigatória") @jakarta.validation.constraints.Size(max=72) String senha
) {
}

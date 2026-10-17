package ink.manager.api.dto;
import jakarta.validation.constraints.*;
public record NovoEstudioRequest(@NotBlank @Size(max = 100) String nomeEstudio,
        @NotBlank @Size(max = 100) String nome, @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String senha) {}

package ink.manager.api.dto;

import jakarta.validation.constraints.*;
import ink.manager.api.model.Cliente;

public record ClienteRequest(
        @NotBlank @Size(max = 255) String nome,
        @NotBlank @Size(max = 30) String telefone,
        @Min(0) @Max(150) Integer idade,
        @Size(max = 100) String instagram,
        @PositiveOrZero Long version) {

    public Cliente toModel() {
        var cliente = new Cliente(nome.trim(), telefone.trim(), idade,
                instagram == null ? null : instagram.trim());
        cliente.setVersion(version);
        return cliente;
    }
}

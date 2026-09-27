package ink.manager.api.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import ink.manager.api.model.Cliente;
import ink.manager.api.model.StatusMarcacao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record MarcacaoComClienteRequest(
        @NotNull(message = "Cliente é obrigatório") @Valid Cliente cliente,
        @NotNull(message = "Data é obrigatória") LocalDate data,
        @NotNull(message = "Horário é obrigatório") LocalTime horario,
        String descricao,
        @NotNull(message = "Status é obrigatório") StatusMarcacao status) {
}

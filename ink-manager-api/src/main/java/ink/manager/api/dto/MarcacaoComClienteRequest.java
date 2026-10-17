package ink.manager.api.dto;
import java.time.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ink.manager.api.model.StatusMarcacao;
public record MarcacaoComClienteRequest(@NotNull @Valid ClienteRequest cliente,
 @NotNull LocalDate data, @NotNull LocalTime horario, @Size(max=255) String descricao,
 @NotNull StatusMarcacao status, @Min(1) @Max(720) Integer duracaoMinutos,
 @PositiveOrZero Long version, @NotNull @Positive Long profissionalId) {}

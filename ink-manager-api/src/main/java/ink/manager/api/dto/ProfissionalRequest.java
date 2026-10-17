package ink.manager.api.dto;
import java.time.LocalTime;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
public record ProfissionalRequest(@NotBlank @Size(max=100) String nome,@NotNull Boolean ativo,
        @NotNull @Size(max=28) List<@NotNull @Valid Turno> horarios,@PositiveOrZero Long version){
    public record Turno(@Min(1) @Max(7) int diaSemana,@NotNull LocalTime inicio,@NotNull LocalTime fim){}
}

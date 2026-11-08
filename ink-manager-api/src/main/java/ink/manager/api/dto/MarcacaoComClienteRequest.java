package ink.manager.api.dto;
import java.time.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ink.manager.api.model.StatusMarcacao;
public record MarcacaoComClienteRequest(@NotNull @Valid ClienteRequest cliente,
 @NotNull LocalDate data, @NotNull LocalTime horario, @Size(max=255) String descricao,
 @NotNull StatusMarcacao status, @Min(1) @Max(720) Integer duracaoMinutos,
 @PositiveOrZero Long version, @NotNull @Positive Long profissionalId,@Positive Long servicoId) {
 public MarcacaoComClienteRequest(ClienteRequest cliente,LocalDate data,LocalTime horario,String descricao,StatusMarcacao status,
        Integer duracaoMinutos,Long version,Long profissionalId){this(cliente,data,horario,descricao,status,duracaoMinutos,version,profissionalId,null);}
}

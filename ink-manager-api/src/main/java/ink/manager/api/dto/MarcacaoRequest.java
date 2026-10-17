package ink.manager.api.dto;
import java.time.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import ink.manager.api.model.*;
public record MarcacaoRequest(@NotNull @Valid ClienteReferencia cliente,
 @NotNull LocalDate data, @NotNull LocalTime horario, @Size(max=255) String descricao,
 @NotNull StatusMarcacao status, @Min(1) @Max(720) Integer duracaoMinutos,
 @PositiveOrZero Long version, @NotNull @Positive Long profissionalId) {
 public Marcacao toModel() { var c=new Cliente(); c.setId(cliente.id()); var m=new Marcacao(c,data,horario,descricao,status); m.setDuracaoMinutos(duracaoMinutos == null ? 60 : duracaoMinutos); m.setVersion(version); var profissional = new Profissional(); profissional.setId(profissionalId); m.setProfissional(profissional); return m; }
}

package ink.manager.api.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record ServicoRequest(@NotBlank @Size(max=100) String nome,
        @NotNull @DecimalMin("0.00") @Digits(integer=10,fraction=2) BigDecimal preco,
        @NotNull @Min(1) @Max(720) Integer duracaoMinutos, boolean ativo, Long version){}

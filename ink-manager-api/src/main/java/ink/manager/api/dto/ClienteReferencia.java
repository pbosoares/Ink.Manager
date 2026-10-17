package ink.manager.api.dto;
import jakarta.validation.constraints.*;
public record ClienteReferencia(@NotNull @Positive Long id) {}

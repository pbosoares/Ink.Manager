package ink.manager.api.dto;
import java.time.LocalDateTime;
import jakarta.validation.constraints.*;
public record BloqueioRequest(@NotNull LocalDateTime inicio,@NotNull LocalDateTime fim,@Size(max=255) String motivo){}

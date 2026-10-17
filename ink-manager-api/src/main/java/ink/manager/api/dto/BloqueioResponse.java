package ink.manager.api.dto;
import java.time.LocalDateTime;
import ink.manager.api.model.BloqueioAgenda;
public record BloqueioResponse(Long id,LocalDateTime inicio,LocalDateTime fim,String motivo){
    public static BloqueioResponse from(BloqueioAgenda b){return new BloqueioResponse(b.getId(),b.getInicio(),b.getFim(),b.getMotivo());}
}

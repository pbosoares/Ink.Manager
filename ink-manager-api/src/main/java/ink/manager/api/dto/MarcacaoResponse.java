package ink.manager.api.dto;
import java.time.*;
import ink.manager.api.model.*;
public record MarcacaoResponse(Long id,ClienteResponse cliente,LocalDate data,LocalTime horario,String descricao,
        StatusMarcacao status,Integer duracaoMinutos,Long version,ProfissionalResumo profissional){
    public record ProfissionalResumo(Long id,String nome,boolean ativo){}
    public static MarcacaoResponse from(Marcacao m){var p=m.getProfissional();return new MarcacaoResponse(m.getId(),ClienteResponse.from(m.getCliente()),m.getData(),m.getHorario(),m.getDescricao(),m.getStatus(),m.getDuracaoMinutos(),m.getVersion(),new ProfissionalResumo(p.getId(),p.getNome(),p.isAtivo()));}
}

package ink.manager.api.dto;
import java.util.List;
import ink.manager.api.model.*;
public record ProfissionalResponse(Long id,String nome,boolean ativo,Long version,List<ProfissionalRequest.Turno> horarios){
    public static ProfissionalResponse from(Profissional p,List<HorarioTrabalho> horarios){
        return new ProfissionalResponse(p.getId(),p.getNome(),p.isAtivo(),p.getVersion(),horarios.stream()
            .map(h->new ProfissionalRequest.Turno(h.getDiaSemana(),h.getInicio(),h.getFim())).toList());
    }
}

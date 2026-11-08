package ink.manager.api.dto;
import java.time.*;
import ink.manager.api.model.*;
public record MarcacaoResponse(Long id,ClienteResponse cliente,LocalDate data,LocalTime horario,String descricao,
        StatusMarcacao status,Integer duracaoMinutos,Long version,ProfissionalResumo profissional,String servicoNome,Long servicoId,
        java.math.BigDecimal precoPrevisto,boolean recebimentoConfirmado,boolean podeConfirmarRecebimento,String presenca){
    public MarcacaoResponse(Long id,ClienteResponse cliente,LocalDate data,LocalTime horario,String descricao,
            StatusMarcacao status,Integer duracaoMinutos,Long version,ProfissionalResumo profissional,String servicoNome,
            java.math.BigDecimal precoPrevisto,boolean recebimentoConfirmado,boolean podeConfirmarRecebimento,String presenca){
        this(id,cliente,data,horario,descricao,status,duracaoMinutos,version,profissional,servicoNome,null,
                precoPrevisto,recebimentoConfirmado,podeConfirmarRecebimento,presenca);
    }
    public record ProfissionalResumo(Long id,String nome,boolean ativo){}
    public static MarcacaoResponse from(Marcacao m){var p=m.getProfissional();return new MarcacaoResponse(m.getId(),ClienteResponse.from(m.getCliente()),m.getData(),m.getHorario(),m.getDescricao(),m.getStatus(),m.getDuracaoMinutos(),m.getVersion(),new ProfissionalResumo(p.getId(),p.getNome(),p.isAtivo()),m.getServicoNome(),m.getServico()==null?null:m.getServico().getId(),m.getPrecoPrevisto(),m.isRecebimentoConfirmado(),m.isPodeConfirmarRecebimento(),m.getPresenca());}
}

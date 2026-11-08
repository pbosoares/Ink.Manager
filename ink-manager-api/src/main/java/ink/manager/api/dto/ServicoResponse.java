package ink.manager.api.dto;
import java.math.BigDecimal;
import java.util.List;
import ink.manager.api.model.Servico;
public record ServicoResponse(Long id,String nome,BigDecimal preco,Integer duracaoMinutos,boolean ativo,Long version,
        List<Long> profissionalIds){
    public ServicoResponse {
        profissionalIds = profissionalIds == null ? List.of() : List.copyOf(profissionalIds);
    }
    public ServicoResponse(Long id,String nome,BigDecimal preco,Integer duracaoMinutos,boolean ativo,Long version){
        this(id,nome,preco,duracaoMinutos,ativo,version,List.of());
    }
    public static ServicoResponse from(Servico s){return from(s,List.of());}
    public static ServicoResponse from(Servico s,List<Long> profissionalIds){
        return new ServicoResponse(s.getId(),s.getNome(),s.getPreco(),s.getDuracaoMinutos(),s.isAtivo(),s.getVersion(),profissionalIds);
    }
}

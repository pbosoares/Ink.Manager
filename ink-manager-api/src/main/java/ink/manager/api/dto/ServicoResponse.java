package ink.manager.api.dto;
import java.math.BigDecimal;
import ink.manager.api.model.Servico;
public record ServicoResponse(Long id,String nome,BigDecimal preco,Integer duracaoMinutos,boolean ativo,Long version){
    public static ServicoResponse from(Servico s){return new ServicoResponse(s.getId(),s.getNome(),s.getPreco(),s.getDuracaoMinutos(),s.isAtivo(),s.getVersion());}
}

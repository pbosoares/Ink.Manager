package ink.manager.api.dto;
import ink.manager.api.model.Cliente;
public record ClienteResponse(Long id,String nome,String telefone,Integer idade,String instagram,Long version) {
 public static ClienteResponse from(Cliente c) { return new ClienteResponse(c.getId(),c.getNome(),c.getTelefone(),c.getIdade(),c.getInstagram(),c.getVersion()); }
}

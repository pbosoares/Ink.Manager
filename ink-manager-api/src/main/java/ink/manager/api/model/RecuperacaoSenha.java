package ink.manager.api.model;
import java.time.LocalDateTime;
import jakarta.persistence.*;
@Entity @Table(name="recuperacoes_senha")
public class RecuperacaoSenha {
    @Id @Column(length=64) private String tokenHash;
    @ManyToOne(optional=false) @JoinColumn(name="usuario_id") private Usuario usuario;
    private LocalDateTime prazo;
    private boolean consumido;
    @Version private Long version;
    protected RecuperacaoSenha(){}
    public RecuperacaoSenha(String hash,Usuario usuario,LocalDateTime prazo){this.tokenHash=hash;this.usuario=usuario;this.prazo=prazo;}
    public Usuario getUsuario(){return usuario;}
    public LocalDateTime getPrazo(){return prazo;}
    public boolean isConsumido(){return consumido;}
    public void consumir(){consumido=true;}
}

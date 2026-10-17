package ink.manager.api.model;
import jakarta.persistence.*;
@Entity @Table(name="profissionais")
public class Profissional {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="estudio_id") private Estudio estudio;
    @Column(nullable=false,length=100) private String nome;
    private boolean ativo=true;
    @Version private Long version;
    private long configuracoes;
    public void marcarConfiguracao(){configuracoes++;}
    public Long getId(){return id;}
    public void setId(Long id){this.id=id;}
    public Estudio getEstudio(){return estudio;}
    public void setEstudio(Estudio estudio){this.estudio=estudio;}
    public String getNome(){return nome;}
    public void setNome(String nome){this.nome=nome;}
    public boolean isAtivo(){return ativo;}
    public void setAtivo(boolean ativo){this.ativo=ativo;}
    public Long getVersion(){return version;}
}

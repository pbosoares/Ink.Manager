package ink.manager.api.model;
import jakarta.persistence.*;
@Entity
@Table(name = "estudios")
public class Estudio {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 100)
    private String nome;
    @Version private Long version;
    @Column(nullable=false) private java.time.LocalDateTime criadoEm;
    private java.time.LocalDateTime pagoAte;
    public java.time.LocalDateTime getCriadoEm(){return criadoEm;}
    public void setCriadoEm(java.time.LocalDateTime criadoEm){this.criadoEm=criadoEm;}
    public java.time.LocalDateTime getPagoAte(){return pagoAte;}

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public Long getVersion() { return version; }
}

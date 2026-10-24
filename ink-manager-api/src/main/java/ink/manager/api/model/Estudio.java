package ink.manager.api.model;
import jakarta.persistence.*;
@Entity
@Table(name = "estudios")
public class Estudio {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable=false, unique=true, length=80)
    private String linkPublico;
    @PrePersist void prepararLink() { if (linkPublico == null) linkPublico = java.util.UUID.randomUUID().toString(); }
    public String getLinkPublico() { return linkPublico; }
    @Column(name="encerrado_em")
    private java.time.Instant encerradoEm;
    public java.time.Instant getEncerradoEm() { return encerradoEm; }
    public boolean isEncerrado() { return encerradoEm != null; }
    public void encerrar(java.time.Instant quando, String novoLinkPublico) {
        if (encerradoEm == null) {
            encerradoEm = java.util.Objects.requireNonNull(quando);
            linkPublico = java.util.Objects.requireNonNull(novoLinkPublico);
        }
    }
    @Column(nullable = false, length = 100)
    private String nome;
    @Version private Long version;
    @Column(nullable=false) private java.time.LocalDateTime criadoEm;
    private java.time.LocalDateTime pagoAte;
    public java.time.LocalDateTime getCriadoEm(){return criadoEm;}
    public void setCriadoEm(java.time.LocalDateTime criadoEm){this.criadoEm=criadoEm;}
    public java.time.LocalDateTime getPagoAte(){return pagoAte;}
    @Column(nullable=false,length=64) private String fusoHorario="America/Sao_Paulo";
    @Column(nullable=false) private Integer antecedenciaMinimaMinutos=120;
    @Column(nullable=false) private Integer limiteAgendamentosEmail=3;
    public String getFusoHorario(){return fusoHorario;}
    public void setFusoHorario(String value){fusoHorario=value;}
    public Integer getAntecedenciaMinimaMinutos(){return antecedenciaMinimaMinutos;}
    public void setAntecedenciaMinimaMinutos(Integer value){antecedenciaMinimaMinutos=value;}
    public Integer getLimiteAgendamentosEmail(){return limiteAgendamentosEmail;}
    public void setLimiteAgendamentosEmail(Integer value){limiteAgendamentosEmail=value;}

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public Long getVersion() { return version; }
}

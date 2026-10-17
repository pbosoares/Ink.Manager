package ink.manager.api.model;
import java.time.LocalDateTime;
import jakarta.persistence.*;
@Entity @Table(name="bloqueios_agenda")
public class BloqueioAgenda {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="profissional_id") private Profissional profissional;
    private LocalDateTime inicio;
    private LocalDateTime fim;
    private String motivo;
    protected BloqueioAgenda(){}
    public BloqueioAgenda(Profissional profissional,LocalDateTime inicio,LocalDateTime fim,String motivo){this.profissional=profissional;this.inicio=inicio;this.fim=fim;this.motivo=motivo;}
    public Long getId(){return id;}
    public LocalDateTime getInicio(){return inicio;}
    public LocalDateTime getFim(){return fim;}
    public String getMotivo(){return motivo;}
}

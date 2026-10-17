package ink.manager.api.model;
import java.time.LocalTime;
import jakarta.persistence.*;
@Entity @Table(name="horarios_trabalho")
public class HorarioTrabalho {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="profissional_id") private Profissional profissional;
    private int diaSemana;
    private LocalTime inicio;
    private LocalTime fim;
    protected HorarioTrabalho(){}
    public HorarioTrabalho(Profissional profissional,int diaSemana,LocalTime inicio,LocalTime fim){this.profissional=profissional;this.diaSemana=diaSemana;this.inicio=inicio;this.fim=fim;}
    public int getDiaSemana(){return diaSemana;}
    public LocalTime getInicio(){return inicio;}
    public LocalTime getFim(){return fim;}
}

package ink.manager.api.model;

import jakarta.persistence.*;
import java.time.*;

@Entity
@Table(name="ofertas_lista_espera")
public class OfertaListaEspera {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="estudio_id",nullable=false) private Estudio estudio;
    @ManyToOne(optional=false) @JoinColumn(name="lista_espera_id",nullable=false) private ListaEspera entrada;
    @ManyToOne(optional=false) @JoinColumn(name="marcacao_cancelada_id",nullable=false) private Marcacao marcacaoCancelada;
    @ManyToOne @JoinColumn(name="reserva_id") private Marcacao reserva;
    @Column(nullable=false) private LocalDate data;
    @Column(nullable=false) private LocalTime horario;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private EstadoOfertaListaEspera estado;
    @Column(name="token_hash",length=64) private String tokenHash;
    @Column(name="token_prazo") private Instant tokenPrazo;
    @Column(name="criado_em",nullable=false) private Instant criadoEm;
    @Version private Long version;
    public OfertaListaEspera(){}
    public Long getId(){return id;} public Estudio getEstudio(){return estudio;} public void setEstudio(Estudio v){estudio=v;}
    public ListaEspera getEntrada(){return entrada;} public void setEntrada(ListaEspera v){entrada=v;}
    public Marcacao getMarcacaoCancelada(){return marcacaoCancelada;} public void setMarcacaoCancelada(Marcacao v){marcacaoCancelada=v;}
    public Marcacao getReserva(){return reserva;} public void setReserva(Marcacao v){reserva=v;}
    public LocalDate getData(){return data;} public void setData(LocalDate v){data=v;}
    public LocalTime getHorario(){return horario;} public void setHorario(LocalTime v){horario=v;}
    public EstadoOfertaListaEspera getEstado(){return estado;} public void setEstado(EstadoOfertaListaEspera v){estado=v;}
    public String getTokenHash(){return tokenHash;} public void setTokenHash(String v){tokenHash=v;}
    public Instant getTokenPrazo(){return tokenPrazo;} public void setTokenPrazo(Instant v){tokenPrazo=v;}
    public Instant getCriadoEm(){return criadoEm;} public void setCriadoEm(Instant v){criadoEm=v;}
}

package ink.manager.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name="lista_espera")
public class ListaEspera {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="estudio_id",nullable=false) private Estudio estudio;
    @ManyToOne(optional=false) @JoinColumn(name="servico_id",nullable=false) private Servico servico;
    @ManyToOne @JoinColumn(name="profissional_id") private Profissional profissional;
    @Column(name="inicio_desejado",nullable=false) private OffsetDateTime inicioDesejado;
    @Column(name="fim_desejado",nullable=false) private OffsetDateTime fimDesejado;
    @Column(nullable=false,length=100) private String nome;
    @Column(nullable=false,length=254) private String email;
    @Column(nullable=false,length=30) private String telefone;
    @Column(name="chave_idempotencia",nullable=false) private UUID chaveIdempotencia;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private EstadoListaEspera estado;
    @Column(name="criado_em",nullable=false) private Instant criadoEm;
    @Version private Long version;
    public ListaEspera() {}
    public Long getId(){return id;} public Estudio getEstudio(){return estudio;} public void setEstudio(Estudio v){estudio=v;}
    public Servico getServico(){return servico;} public void setServico(Servico v){servico=v;}
    public Profissional getProfissional(){return profissional;} public void setProfissional(Profissional v){profissional=v;}
    public OffsetDateTime getInicioDesejado(){return inicioDesejado;} public void setInicioDesejado(OffsetDateTime v){inicioDesejado=v;}
    public OffsetDateTime getFimDesejado(){return fimDesejado;} public void setFimDesejado(OffsetDateTime v){fimDesejado=v;}
    public String getNome(){return nome;} public void setNome(String v){nome=v;}
    public String getEmail(){return email;} public void setEmail(String v){email=v;}
    public String getTelefone(){return telefone;} public void setTelefone(String v){telefone=v;}
    public UUID getChaveIdempotencia(){return chaveIdempotencia;} public void setChaveIdempotencia(UUID v){chaveIdempotencia=v;}
    public EstadoListaEspera getEstado(){return estado;} public void setEstado(EstadoListaEspera v){estado=v;}
    public Instant getCriadoEm(){return criadoEm;} public void setCriadoEm(Instant v){criadoEm=v;}
}

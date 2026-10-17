package ink.manager.api.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "marcacoes")
public class Marcacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.ManyToOne(optional = false)
    @jakarta.persistence.JoinColumn(name = "estudio_id", nullable = false)
    private Estudio estudio;
    public Estudio getEstudio() { return estudio; }
    public void setEstudio(Estudio estudio) { this.estudio = estudio; }


    @jakarta.persistence.Version
    private Long version;
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }


    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = false)
    @NotNull(message = "Cliente é obrigatório")
    private Cliente cliente;
    @ManyToOne(optional=false) @JoinColumn(name="profissional_id",nullable=false)
    private Profissional profissional;
    public Profissional getProfissional(){return profissional;}
    public void setProfissional(Profissional profissional){this.profissional=profissional;}


    @NotNull(message = "Data é obrigatória")
    private LocalDate data;

    @NotNull(message = "Horário é obrigatório")
    private LocalTime horario;

    @jakarta.validation.constraints.Size(max = 255)
    private String descricao;

    @Column(nullable=false)
    @jakarta.validation.constraints.Min(1)
    @jakarta.validation.constraints.Max(720)
    private Integer duracaoMinutos = 60;
    public Integer getDuracaoMinutos() { return duracaoMinutos; }
    public void setDuracaoMinutos(Integer value) { duracaoMinutos=value; }


    @Enumerated(EnumType.STRING)
    @NotNull(message = "Status é obrigatório")
    private StatusMarcacao status;

    public Marcacao() {
    }

    public Marcacao(
            Cliente cliente,
            LocalDate data,
            LocalTime horario,
            @jakarta.validation.constraints.Size(max = 255) String descricao,
            StatusMarcacao status) {

        this.cliente = cliente;
        this.data = data;
        this.horario = horario;
        this.descricao = descricao;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
    }

    public LocalDate getData() {
        return data;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public LocalTime getHorario() {
        return horario;
    }

    public void setHorario(LocalTime horario) {
        this.horario = horario;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public StatusMarcacao getStatus() {
        return status;
    }

    public void setStatus(StatusMarcacao status) {
        this.status = status;
    }
}

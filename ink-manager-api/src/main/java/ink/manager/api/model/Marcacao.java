package ink.manager.api.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "marcacoes")
public class Marcacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    private LocalDate data;

    private LocalTime horario;

    private String descricao;

    @Enumerated(EnumType.STRING)
    private StatusMarcacao status;

    public Marcacao() {
    }

    public Marcacao(
            Cliente cliente,
            LocalDate data,
            LocalTime horario,
            String descricao,
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
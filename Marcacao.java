import java.time.LocalDate;
import java.time.LocalTime;

public class Marcacao {

    private int id;
    private int clienteId;
    private LocalDate data;
    private LocalTime horario;
    private String descricao;
    private StatusMarcacao status;

    public Marcacao() {
    }

    public Marcacao(int clienteId, LocalDate data, LocalTime horario,
            String descricao, StatusMarcacao status) {
        this.clienteId = clienteId;
        this.data = data;
        this.horario = horario;
        this.descricao = descricao;
        this.status = status;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getClienteId() {
        return clienteId;
    }

    public void setClienteId(int clienteId) {
        this.clienteId = clienteId;
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
package ink.manager.api.dto;
import java.time.LocalDate;
import java.time.LocalTime;
public record RegistroFalta(LocalDate data,LocalTime horario,String servico,String profissional) {}

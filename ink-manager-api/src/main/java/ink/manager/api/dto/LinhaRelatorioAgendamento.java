package ink.manager.api.dto;
import java.math.BigDecimal;
import java.time.LocalDate;
import ink.manager.api.model.StatusMarcacao;
public record LinhaRelatorioAgendamento(LocalDate data,StatusMarcacao status,String presenca,
        BigDecimal precoPrevisto,String servicoNome,Long servicoId) {}

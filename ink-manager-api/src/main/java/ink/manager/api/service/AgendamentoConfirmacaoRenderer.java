package ink.manager.api.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import ink.manager.api.model.StatusMarcacao;
import ink.manager.api.repository.MarcacaoRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Immediate customer confirmation with the appointment details, rendered from current tenant data. */
@Component
public class AgendamentoConfirmacaoRenderer implements EmailOutboxRenderer {
    private final ObjectMapper json;
    private final MarcacaoRepository marcacoes;

    public AgendamentoConfirmacaoRenderer(ObjectMapper json, MarcacaoRepository marcacoes) {
        this.json = json;
        this.marcacoes = marcacoes;
    }

    @Override
    public boolean suporta(String tipo) {
        return "CONFIRMACAO_AGENDAMENTO".equals(tipo);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean obsoleto(EmailOutboxJob job) {
        var marcacao = marcacaoAtual(job);
        if (marcacao == null || (marcacao.getStatus() != StatusMarcacao.AGENDADA
                && marcacao.getStatus() != StatusMarcacao.CONFIRMADA)) return true;
        String atual = marcacao.getData() + "T" + marcacao.getHorario() + "/" + marcacao.getDuracaoMinutos();
        try {
            JsonNode payload = json.readTree(job.dadosJson());
            return !payload.path("inicio").asText().equals(atual)
                    || payload.path("duracaoMinutos").asInt(-1) != marcacao.getDuracaoMinutos()
                    || marcacao.getCliente() == null || marcacao.getCliente().getEmail() == null
                    || !marcacao.getCliente().getEmail().equalsIgnoreCase(job.destinatario());
        } catch (Exception invalidPayload) {
            return true;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public EmailOutboxConteudo render(EmailOutboxJob job) {
        var marcacao = marcacaoAtual(job);
        if (marcacao == null) throw new IllegalArgumentException("Appointment no longer exists");
        String quando = LocalDateTime.of(marcacao.getData(), marcacao.getHorario())
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy '\u00e0s' HH:mm", Locale.forLanguageTag("pt-BR")));
        String procedimento = Objects.toString(marcacao.getServicoNome(),
                Objects.toString(marcacao.getDescricao(), "Atendimento"));
        String texto = "Sua marca\u00e7\u00e3o foi registrada.\n\n"
                + "Estabelecimento: " + marcacao.getEstudio().getNome() + "\n"
                + "Procedimento: " + procedimento + "\n"
                + "Profissional: " + marcacao.getProfissional().getNome() + "\n"
                + "Data e hor\u00e1rio: " + quando + " (" + marcacao.getEstudio().getFusoHorario() + ")\n"
                + "Dura\u00e7\u00e3o: " + marcacao.getDuracaoMinutos() + " minutos\n\n"
                + "Guarde este email com os detalhes do seu atendimento.";
        return new EmailOutboxConteudo("Sua marca\u00e7\u00e3o foi registrada", texto);
    }

    private ink.manager.api.model.Marcacao marcacaoAtual(EmailOutboxJob job) {
        try {
            long id = Long.parseLong(job.referencia());
            return marcacoes.findByIdAndEstudioId(id, job.estudioId()).orElse(null);
        } catch (RuntimeException invalidReference) {
            return null;
        }
    }
}

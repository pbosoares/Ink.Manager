package ink.manager.api.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import ink.manager.api.model.StatusMarcacao;
import ink.manager.api.repository.MarcacaoRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Renders reminder jobs only while their appointment snapshot is still current. */
@Component
public class AgendamentoLembreteRenderer implements EmailOutboxRenderer {
    private final ObjectMapper json;
    private final MarcacaoRepository marcacoes;
    private final EmailActionService acoes;

    public AgendamentoLembreteRenderer(ObjectMapper json, MarcacaoRepository marcacoes, EmailActionService acoes) {
        this.json = json;
        this.marcacoes = marcacoes;
        this.acoes = acoes;
    }

    @Override
    public boolean suporta(String tipo) {
        return "LEMBRETE_24H".equals(tipo) || "LEMBRETE_2H".equals(tipo);
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
    @Transactional
    public EmailOutboxConteudo render(EmailOutboxJob job) {
        long id = Long.parseLong(job.referencia());
        var links = acoes.emitir(job.estudioId(), id);
        var marcacao = marcacoes.findByIdAndEstudioId(id, job.estudioId()).orElseThrow();
        String quando = LocalDateTime.of(marcacao.getData(), marcacao.getHorario())
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR")));
        String servico = marcacao.getServicoNome() == null ? marcacao.getDescricao() : marcacao.getServicoNome();
        String texto = "Seu horário está chegando.\n\n"
                + "DATA E HORA\n"
                + "------------------------------\n"
                + quando + " (" + marcacao.getEstudio().getFusoHorario() + ")\n\n"
                + "DETALHES DO AGENDAMENTO\n"
                + "Serviço: " + servico + "\n"
                + "Profissional: " + marcacao.getProfissional().getNome() + "\n"
                + "Duração: " + marcacao.getDuracaoMinutos() + " minutos\n\n"
                + "AÇÕES DISPONÍVEIS\n\n"
                + "Confirmar agendamento:\n" + links.confirmar() + "\n\n"
                + "Solicitar reagendamento:\n" + links.reagendar() + "\n\n"
                + "Cancelar agendamento:\n" + links.cancelar();
        return new EmailOutboxConteudo("Lembrete: seu agendamento", texto);
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

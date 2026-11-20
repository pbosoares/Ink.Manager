package ink.manager.api.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import tools.jackson.databind.ObjectMapper;
import ink.manager.api.model.Cliente;
import ink.manager.api.model.Estudio;
import ink.manager.api.model.Marcacao;
import ink.manager.api.model.StatusMarcacao;
import ink.manager.api.repository.MarcacaoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgendamentoConfirmacaoRendererTest {
    private static final long TENANT = 73L;
    private static final long MARCACAO = 91L;
    private static final String EMAIL = "cliente@example.com";
    // Atendimento: 30/10/2026 10:00 em America/Sao_Paulo = 13:00Z.
    private static final EmailOutboxJob JOB = new EmailOutboxJob(5, TENANT, EMAIL, "CONFIRMACAO_AGENDAMENTO",
            "confirmacao-key", Instant.parse("2026-10-20T10:00:00Z"), Long.toString(MARCACAO),
            "{\"marcacaoId\":91,\"inicio\":\"2026-10-30T10:00/30\",\"duracaoMinutos\":30}");

    private AgendamentoConfirmacaoRenderer rendererEm(String instante) {
        var repository = mock(MarcacaoRepository.class);
        var cliente = new Cliente();
        cliente.setEmail(EMAIL);
        var estudio = mock(Estudio.class);
        when(estudio.getFusoHorario()).thenReturn("America/Sao_Paulo");
        var marcacao = mock(Marcacao.class);
        when(marcacao.getStatus()).thenReturn(StatusMarcacao.AGENDADA);
        when(marcacao.getData()).thenReturn(LocalDate.of(2026, 10, 30));
        when(marcacao.getHorario()).thenReturn(LocalTime.of(10, 0));
        when(marcacao.getDuracaoMinutos()).thenReturn(30);
        when(marcacao.getCliente()).thenReturn(cliente);
        when(marcacao.getEstudio()).thenReturn(estudio);
        when(repository.findByIdAndEstudioId(MARCACAO, TENANT)).thenReturn(Optional.of(marcacao));
        return new AgendamentoConfirmacaoRenderer(new ObjectMapper(), repository,
                Clock.fixed(Instant.parse(instante), ZoneOffset.UTC));
    }

    @Test void enviaConfirmacaoRecente() {
        assertFalse(rendererEm("2026-10-20T10:01:00Z").obsoleto(JOB));
        assertFalse(rendererEm("2026-10-20T11:59:00Z").obsoleto(JOB));
    }

    @Test void descartaConfirmacaoAcumuladaNaFila() {
        // Reserva feita dias antes de o envio ser ligado: nao manda confirmacao atrasada.
        assertTrue(rendererEm("2026-10-20T12:01:00Z").obsoleto(JOB));
        assertTrue(rendererEm("2026-10-25T09:00:00Z").obsoleto(JOB));
    }

    @Test void descartaConfirmacaoDeAtendimentoQueJaComecou() {
        var jobEmCimaDaHora = new EmailOutboxJob(JOB.id(), TENANT, EMAIL, JOB.tipo(), JOB.chaveIdempotencia(),
                Instant.parse("2026-10-30T12:50:00Z"), JOB.referencia(), JOB.dadosJson());
        assertFalse(rendererEm("2026-10-30T12:55:00Z").obsoleto(jobEmCimaDaHora));
        assertTrue(rendererEm("2026-10-30T13:00:00Z").obsoleto(jobEmCimaDaHora));
    }
}

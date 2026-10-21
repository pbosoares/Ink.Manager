package ink.manager.api.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import tools.jackson.databind.ObjectMapper;
import ink.manager.api.model.Cliente;
import ink.manager.api.model.Estudio;
import ink.manager.api.model.Marcacao;
import ink.manager.api.model.Profissional;
import ink.manager.api.model.StatusMarcacao;
import ink.manager.api.repository.MarcacaoRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgendamentoLembreteRendererTest {
    private static final long TENANT=73L;
    private static final long MARCACAO=91L;
    private static final LocalDate DATA=LocalDate.of(2026,10,30);
    private static final LocalTime HORA=LocalTime.of(10,0);
    private static final String EMAIL="cliente@example.com";

    private record Fixture(AgendamentoLembreteRenderer renderer, Marcacao marcacao,
            MarcacaoRepository repository, EmailActionService acoes, EmailOutboxJob job) {}

    private Fixture fixture() {
        var repository=mock(MarcacaoRepository.class);
        var acoes=mock(EmailActionService.class);
        var cliente=new Cliente();cliente.setEmail(EMAIL);
        var profissional=mock(Profissional.class);when(profissional.getNome()).thenReturn("Ana");
        var estudio=mock(Estudio.class);when(estudio.getFusoHorario()).thenReturn("America/Sao_Paulo");
        var marcacao=mock(Marcacao.class);
        when(marcacao.getStatus()).thenReturn(StatusMarcacao.AGENDADA);
        when(marcacao.getData()).thenReturn(DATA);when(marcacao.getHorario()).thenReturn(HORA);
        when(marcacao.getDuracaoMinutos()).thenReturn(30);when(marcacao.getCliente()).thenReturn(cliente);
        when(marcacao.getProfissional()).thenReturn(profissional);when(marcacao.getEstudio()).thenReturn(estudio);
        when(marcacao.getServicoNome()).thenReturn("Tatuagem pequena");
        when(repository.findByIdAndEstudioId(MARCACAO,TENANT)).thenReturn(Optional.of(marcacao));
        String inicio=DATA+"T"+HORA+"/30";
        var job=new EmailOutboxJob(12,TENANT,EMAIL,"LEMBRETE_24H","reminder-key",Instant.parse("2026-10-29T10:00:00Z"),Long.toString(MARCACAO),
                "{\"marcacaoId\":91,\"inicio\":\""+inicio+"\",\"duracaoMinutos\":30}");
        return new Fixture(new AgendamentoLembreteRenderer(new ObjectMapper(),repository,acoes),marcacao,repository,acoes,job);
    }

    @Test void renderizaLembreteComDadosAtuaisELinksDeAcao() {
        var f=fixture();
        when(f.acoes().emitir(TENANT,MARCACAO)).thenReturn(new EmailActionService.Links(
                "https://local/confirmar","https://local/reagendar","https://local/cancelar",Instant.parse("2026-10-23T10:00:00Z")));
        var content=f.renderer().render(f.job());
        assertEquals("Lembrete do seu agendamento",content.assunto());
        assertTrue(content.texto().contains("Tatuagem pequena"));assertTrue(content.texto().contains("Ana"));
        assertTrue(content.texto().contains("30/10/2026"));assertTrue(content.texto().contains("10:00"));
        assertTrue(content.texto().contains("https://local/confirmar"));assertTrue(content.texto().contains("https://local/reagendar"));
        assertTrue(content.texto().contains("https://local/cancelar"));
        verify(f.acoes()).emitir(TENANT,MARCACAO);
        verify(f.repository()).findByIdAndEstudioId(MARCACAO,TENANT);
    }

    @Test void obsoletoComparaSnapshotERecusaMarcacaoCancelada() {
        var f=fixture();
        assertFalse(f.renderer().obsoleto(f.job()));
        var staleJob=new EmailOutboxJob(f.job().id(),TENANT,EMAIL,f.job().tipo(),f.job().chaveIdempotencia(),
                f.job().disponivelEm(),Long.toString(MARCACAO),"{\"inicio\":\"2026-10-30T09:30/30\",\"duracaoMinutos\":30}");
        assertTrue(f.renderer().obsoleto(staleJob));
        when(f.marcacao().getStatus()).thenReturn(StatusMarcacao.CANCELADA);
        assertTrue(f.renderer().obsoleto(f.job()));
    }
}

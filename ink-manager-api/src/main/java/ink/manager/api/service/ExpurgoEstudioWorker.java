package ink.manager.api.service;

import ink.manager.api.repository.EstudioRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Fail-closed retention worker. It never runs unless MARCAE_RETENTION_ENABLED is explicitly true. */
@Component
public class ExpurgoEstudioWorker {
    private static final Logger log = LoggerFactory.getLogger(ExpurgoEstudioWorker.class);
    private static final ZoneId ZONA_ANO_CALENDARIO = ZoneId.of("America/Sao_Paulo");
    private static final int ANOS_FINANCEIROS_APOS_ANO_CALENDARIO = 5;
    private final JdbcTemplate jdbc;
    private final EstudioRepository estudios;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final boolean enabled;
    private final int lote;
    private final ManifestoRestauracaoGate gateRestauracao;

    public ExpurgoEstudioWorker(JdbcTemplate jdbc, EstudioRepository estudios, PlatformTransactionManager manager,
            Clock clock,
            @Value("${marcae.retencao.worker-ativo:${MARCAE_RETENTION_ENABLED:false}}") boolean enabled,
            @Value("${marcae.retencao.lote:10}") int lote,
            ManifestoEncerramentoStore manifesto, ManifestoRestauracaoGate gateRestauracao) {
        if (lote < 1 || lote > 100)
            throw new IllegalArgumentException("Invalid data-retention worker configuration");
        if (enabled && !manifesto.configurado())
            throw new IllegalArgumentException("MARCAE_RETENTION_ENABLED requires a configured external closure manifest");
        this.jdbc = jdbc;
        this.estudios = estudios;
        this.tx = new TransactionTemplate(manager);
        this.clock = clock;
        this.enabled = enabled;
        this.lote = lote;
        this.gateRestauracao = gateRestauracao;
    }

    @Scheduled(fixedDelayString = "${marcae.retencao.intervalo-ms:60000}")
    public void poll() {
        if (!enabled || gateRestauracao.bloqueado()) return;
        Instant now = clock.instant();
        List<Long> ids = jdbc.queryForList("SELECT e.estudio_id FROM expurgos_estudio e " +
                "WHERE (e.proxima_tentativa_em IS NULL OR e.proxima_tentativa_em <= ?) AND (" +
                "(e.lista_espera_concluido_em IS NULL AND e.lista_espera_ate <= ?) OR " +
                "(e.operacional_concluido_em IS NULL AND e.operacional_ate <= ?) OR " +
                "(e.operacional_concluido_em IS NOT NULL AND e.recebimentos_arquivo_concluido_em IS NULL AND (" +
                "NOT EXISTS (SELECT 1 FROM recebimentos_arquivo a WHERE a.estudio_id = e.estudio_id) OR " +
                "EXISTS (SELECT 1 FROM recebimentos_arquivo a WHERE a.estudio_id = e.estudio_id AND a.expira_em <= ? " +
                "AND NOT EXISTS (SELECT 1 FROM retencoes_legais_financeiras h WHERE h.estudio_id = a.estudio_id " +
                "AND h.recebimento_origem_id = a.recebimento_origem_id AND h.encerrada_em IS NULL))))) " +
                "ORDER BY e.estudio_id FETCH FIRST ? ROWS ONLY", Long.class,
                utc(now), utc(now), utc(now), utc(now), lote);
        for (Long id : ids) {
            try {
                tx.executeWithoutResult(status -> processar(id, clock.instant()));
            } catch (RuntimeException failure) {
                registrarFalha(id);
                log.error("Data-retention step failed; it remains queued for retry. estudio_id={}", id);
            }
        }
    }

    private void processar(long estudioId, Instant agora) {
        var estudio = estudios.bloquear(estudioId).orElse(null);
        if (estudio == null || !estudio.isEncerrado()) return;
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM expurgos_estudio WHERE estudio_id = ? FOR UPDATE", estudioId);
        Instant filaAte = instant(row.get("lista_espera_ate"));
        Instant operacionalAte = instant(row.get("operacional_ate"));
        Instant filaConcluida = instant(row.get("lista_espera_concluido_em"));
        Instant operacionalConcluido = instant(row.get("operacional_concluido_em"));
        Instant recebimentosArquivoConcluido = instant(row.get("recebimentos_arquivo_concluido_em"));

        if (filaConcluida == null && !filaAte.isAfter(agora)) {
            expurgarFilaEspera(estudioId);
            jdbc.update("UPDATE expurgos_estudio SET lista_espera_concluido_em = ? WHERE estudio_id = ?",
                    utc(agora), estudioId);
            filaConcluida = agora;
        }

        if (operacionalConcluido == null && !operacionalAte.isAfter(agora)) {
            if (filaConcluida == null) {
                expurgarFilaEspera(estudioId);
                jdbc.update("UPDATE expurgos_estudio SET lista_espera_concluido_em = ? WHERE estudio_id = ?",
                        utc(agora), estudioId);
            }
            arquivarRecebimentos(estudioId, agora);
            expurgarDadosOperacionais(estudioId);
            jdbc.update("UPDATE estudios SET nome = 'Conta encerrada', fuso_horario = 'UTC', " +
                    "antecedencia_minima_minutos = 120, limite_agendamentos_email = 3 WHERE id = ?", estudioId);
            jdbc.update("UPDATE assinatura_checkouts SET checkout_url = NULL WHERE estudio_id = ?", estudioId);
            jdbc.update("UPDATE expurgos_estudio SET operacional_concluido_em = ? WHERE estudio_id = ?",
                    utc(agora), estudioId);
            operacionalConcluido = agora;
        }

        if (operacionalConcluido != null && recebimentosArquivoConcluido == null) {
            expurgarRecebimentosVencidos(estudioId, agora);
            Integer restantes = jdbc.queryForObject("SELECT COUNT(*) FROM recebimentos_arquivo WHERE estudio_id = ?",
                    Integer.class, estudioId);
            if (restantes != null && restantes == 0)
                jdbc.update("UPDATE expurgos_estudio SET recebimentos_arquivo_concluido_em = ? WHERE estudio_id = ?",
                        utc(agora), estudioId);
        }

        jdbc.update("UPDATE expurgos_estudio SET tentativas = 0, proxima_tentativa_em = NULL, ultimo_erro = NULL " +
                "WHERE estudio_id = ?", estudioId);
    }

    private void expurgarFilaEspera(long estudioId) {
        // Offers reference both the wait-list row and bookings; delete them before either parent.
        jdbc.update("DELETE FROM ofertas_lista_espera WHERE estudio_id = ?", estudioId);
        jdbc.update("DELETE FROM lista_espera WHERE estudio_id = ?", estudioId);
        // All queued message bodies include recipient or booking data.
        jdbc.update("DELETE FROM email_outbox WHERE estudio_id = ?", estudioId);
        jdbc.update("DELETE FROM email_action_tokens WHERE estudio_id = ?", estudioId);
    }

    private void arquivarRecebimentos(long estudioId, Instant agora) {
        List<RecebimentoMinimo> originais = jdbc.query(
                "SELECT id, valor, confirmado_em FROM recebimentos WHERE estudio_id = ? ORDER BY id",
                (rs, row) -> new RecebimentoMinimo(rs.getLong("id"), rs.getBigDecimal("valor"),
                        rs.getTimestamp("confirmado_em").toInstant()), estudioId);
        Set<Long> jaArquivados = new HashSet<>(jdbc.queryForList(
                "SELECT recebimento_origem_id FROM recebimentos_arquivo WHERE estudio_id = ?", Long.class, estudioId));
        List<Object[]> pendentes = new ArrayList<>();
        for (RecebimentoMinimo recebido : originais) {
            if (jaArquivados.contains(recebido.id())) continue;
            int ano = LocalDate.ofInstant(recebido.confirmadoEm(), ZONA_ANO_CALENDARIO).getYear();
            Instant expiraEm = LocalDate.of(ano + ANOS_FINANCEIROS_APOS_ANO_CALENDARIO + 1, 1, 1)
                    .atStartOfDay(ZONA_ANO_CALENDARIO).toInstant();
            pendentes.add(new Object[] { recebido.id(), estudioId, recebido.valor(), utc(recebido.confirmadoEm()),
                    utc(agora), ano, utc(expiraEm) });
        }
        if (!pendentes.isEmpty()) {
            jdbc.batchUpdate("INSERT INTO recebimentos_arquivo (recebimento_origem_id, estudio_id, valor, confirmado_em, moeda, arquivado_em, ano_calendario, expira_em) " +
                    "VALUES (?, ?, ?, ?, 'BRL', ?, ?, ?)", pendentes);
        }
    }

    private void expurgarRecebimentosVencidos(long estudioId, Instant agora) {
        List<Long> vencidosSemHold = jdbc.queryForList("SELECT a.recebimento_origem_id FROM recebimentos_arquivo a " +
                "WHERE a.estudio_id = ? AND a.expira_em <= ? AND NOT EXISTS (SELECT 1 FROM retencoes_legais_financeiras h " +
                "WHERE h.estudio_id = a.estudio_id AND h.recebimento_origem_id = a.recebimento_origem_id " +
                "AND h.encerrada_em IS NULL)", Long.class, estudioId, utc(agora));
        if (vencidosSemHold.isEmpty()) return;

        List<Object[]> holdsEncerrados = vencidosSemHold.stream().map(id -> new Object[] { estudioId, id }).toList();
        jdbc.batchUpdate("DELETE FROM retencoes_legais_financeiras WHERE estudio_id = ? " +
                "AND recebimento_origem_id = ? AND encerrada_em IS NOT NULL", holdsEncerrados);
        jdbc.update("DELETE FROM recebimentos_arquivo WHERE estudio_id = ? AND expira_em <= ? " +
                "AND NOT EXISTS (SELECT 1 FROM retencoes_legais_financeiras h " +
                "WHERE h.estudio_id = recebimentos_arquivo.estudio_id " +
                "AND h.recebimento_origem_id = recebimentos_arquivo.recebimento_origem_id " +
                "AND h.encerrada_em IS NULL)", estudioId, utc(agora));
    }

    private record RecebimentoMinimo(long id, BigDecimal valor, Instant confirmadoEm) {}

    private void expurgarDadosOperacionais(long estudioId) {
        // Receipts have been reduced to a financial archive with no booking or user FK above.
        jdbc.update("DELETE FROM recebimentos WHERE estudio_id = ?", estudioId);
        jdbc.update("DELETE FROM email_action_tokens WHERE estudio_id = ?", estudioId);
        jdbc.update("DELETE FROM email_outbox WHERE estudio_id = ?", estudioId);
        jdbc.update("DELETE FROM ofertas_lista_espera WHERE estudio_id = ?", estudioId);
        jdbc.update("DELETE FROM lista_espera WHERE estudio_id = ?", estudioId);
        jdbc.update("DELETE FROM recuperacoes_senha WHERE usuario_id IN (SELECT id FROM usuarios WHERE estudio_id = ?)", estudioId);
        jdbc.update("DELETE FROM marcacoes WHERE estudio_id = ?", estudioId);
        jdbc.update("DELETE FROM cliente WHERE estudio_id = ?", estudioId);
        jdbc.update("DELETE FROM horarios_trabalho WHERE profissional_id IN (SELECT id FROM profissionais WHERE estudio_id = ?)", estudioId);
        jdbc.update("DELETE FROM bloqueios_agenda WHERE profissional_id IN (SELECT id FROM profissionais WHERE estudio_id = ?)", estudioId);
        jdbc.update("DELETE FROM profissionais WHERE estudio_id = ?", estudioId);
        jdbc.update("DELETE FROM servicos WHERE estudio_id = ?", estudioId);
        jdbc.update("DELETE FROM usuarios WHERE estudio_id = ?", estudioId);
    }

    private void registrarFalha(long estudioId) {
        try {
            Integer attempt = jdbc.queryForObject("SELECT tentativas FROM expurgos_estudio WHERE estudio_id = ?", Integer.class, estudioId);
            if (attempt == null) return;
            long backoffSeconds = Math.min(21600L, 30L << Math.min(attempt, 9));
            jdbc.update("UPDATE expurgos_estudio SET tentativas = tentativas + 1, proxima_tentativa_em = ?, " +
                    "ultimo_erro = 'EXPURGO_RETRY' WHERE estudio_id = ?",
                    utc(clock.instant().plusSeconds(backoffSeconds)), estudioId);
        } catch (RuntimeException ignored) {
            log.error("Could not persist data-retention retry marker. estudio_id={}", estudioId);
        }
    }

    private static OffsetDateTime utc(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant valueInstant) return valueInstant;
        if (value instanceof OffsetDateTime valueOffset) return valueOffset.toInstant();
        if (value instanceof java.sql.Timestamp valueTimestamp) return valueTimestamp.toInstant();
        throw new IllegalStateException("Unsupported database timestamp type");
    }
}

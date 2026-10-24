package ink.manager.api.service;

import ink.manager.api.config.AcessoAtual;
import ink.manager.api.exception.ApiException;
import ink.manager.api.model.Estudio;
import ink.manager.api.repository.EmailActionTokenRepository;
import ink.manager.api.repository.EstudioRepository;
import ink.manager.api.repository.UsuarioRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Closes a tenant immediately and queues reversible, staged data deletion. */
@Service
public class EncerramentoEstudioService {
    private static final Logger log = LoggerFactory.getLogger(EncerramentoEstudioService.class);
    private static final Pattern POSTGRES_SEQUENCE_NAME = Pattern.compile("[A-Za-z0-9_]+\\.[A-Za-z0-9_]+");
    public record Resultado(String estado, Instant encerradoEm, Instant expurgoListaEsperaEm,
            Instant expurgoOperacionalEm, boolean arquivoFinanceiroAguardaPrazo) {}

    private final EstudioRepository estudios;
    private final UsuarioRepository usuarios;
    private final EmailActionTokenRepository tokens;
    private final EmailOutboxService outbox;
    private final AssinaturaService assinatura;
    private final AcessoAtual acesso;
    private final JdbcTemplate jdbc;
    private final ManifestoEncerramentoStore manifesto;
    private final ManifestoRestauracaoGate gateRestauracao;
    private final TransactionTemplate transacaoReconciliacao;
    private final Clock clock;
    private final int listaEsperaHoras;
    private final int operacionalDias;

    public EncerramentoEstudioService(EstudioRepository estudios, UsuarioRepository usuarios,
            EmailActionTokenRepository tokens, EmailOutboxService outbox, AssinaturaService assinatura,
            AcessoAtual acesso, JdbcTemplate jdbc, Clock clock,
            ManifestoEncerramentoStore manifesto,
            ManifestoRestauracaoGate gateRestauracao,
            PlatformTransactionManager transactionManager,
            @Value("${marcae.retencao.lista-espera-horas:${MARCAE_WAITLIST_RETENTION_HOURS:24}}") int listaEsperaHoras,
            @Value("${marcae.retencao.operacional-dias:${MARCAE_OPERATIONAL_RETENTION_DAYS:30}}") int operacionalDias) {
        this.estudios = estudios;
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.outbox = outbox;
        this.assinatura = assinatura;
        this.acesso = acesso;
        this.jdbc = jdbc;
        this.manifesto = manifesto;
        this.gateRestauracao = gateRestauracao;
        this.transacaoReconciliacao = new TransactionTemplate(transactionManager);
        this.clock = clock;
        if (listaEsperaHoras < 1 || listaEsperaHoras > 168 || operacionalDias < 1 || operacionalDias > 3650
                || (long) listaEsperaHoras > (long) operacionalDias * 24)
            throw new IllegalArgumentException("Invalid data-retention configuration");
        this.listaEsperaHoras = listaEsperaHoras;
        this.operacionalDias = operacionalDias;
    }

    @Transactional
    public Resultado encerrar(String confirmacao) {
        if (!"ENCERRAR".equals(confirmacao))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Confirme o encerramento digitando ENCERRAR.");

        long estudioId = acesso.estudioId();
        Estudio estudio = estudios.bloquear(estudioId).orElseThrow();
        if (estudio.isEncerrado()) return resultadoExistente(estudioId);

        var plano = assinatura.resumo(estudioId);
        LocalDateTime agoraLocal = LocalDateTime.now(clock);
        if (plano.cartaoEmRecorrencia() || (plano.pagoAte() != null && plano.pagoAte().isAfter(agoraLocal)))
            throw new ApiException(HttpStatus.CONFLICT,
                    "Encerre a recorrencia e aguarde o fim do periodo ja pago antes de fechar a conta.");
        Integer checkoutsEmAberto = jdbc.queryForObject("SELECT COUNT(*) FROM assinatura_checkouts WHERE estudio_id = ? " +
                "AND status IN ('pending','authorized','paused','trialing','active','past_due')", Integer.class, estudioId);
        if (checkoutsEmAberto != null && checkoutsEmAberto > 0)
            throw new ApiException(HttpStatus.CONFLICT,
                    "Resolva os checkouts de pagamento ainda abertos antes de fechar a conta.");

        Instant agora = clock.instant();
        Integer emailsEmEntrega = jdbc.queryForObject("SELECT COUNT(*) FROM email_outbox WHERE estudio_id = ? " +
                "AND estado = 'ENVIANDO' AND lease_ate > ?", Integer.class, estudioId, utc(agora));
        if (emailsEmEntrega != null && emailsEmEntrega > 0)
            throw new ApiException(HttpStatus.CONFLICT,
                    "Aguarde a entrega de email em andamento terminar e repita o encerramento.");
        Instant filaAte = agora.plus(Duration.ofHours(listaEsperaHoras));
        Instant operacionalAte = agora.plus(Duration.ofDays(operacionalDias));
        var tombstone = manifesto.registrar(estudioId, agora, filaAte, operacionalAte);
        agora = tombstone.encerradoEm();
        filaAte = tombstone.listaEsperaAte();
        operacionalAte = tombstone.operacionalAte();

        estudio.encerrar(agora, "closed-" + estudioId + "-" + tombstone.sequencia());
        estudios.saveAndFlush(estudio);
        for (var usuario : usuarios.findByEstudioIdOrderByNomeAsc(estudioId)) {
            usuario.setAtivo(false);
            usuario.revogarSessoes();
        }
        jdbc.update("DELETE FROM recuperacoes_senha WHERE usuario_id IN (SELECT id FROM usuarios WHERE estudio_id = ?)", estudioId);
        tokens.excluirDoEstudio(estudioId);
        outbox.invalidateForStudio(estudioId);
        jdbc.update("UPDATE assinatura_checkouts SET checkout_url = NULL WHERE estudio_id = ?", estudioId);
        jdbc.update("INSERT INTO expurgos_estudio (estudio_id, solicitado_em, lista_espera_ate, operacional_ate) " +
                "VALUES (?, ?, ?, ?)", estudioId, utc(agora), utc(filaAte), utc(operacionalAte));

        return new Resultado("ENCERRAMENTO_CONFIRMADO", agora, filaAte, operacionalAte, true);
    }

    private Resultado resultadoExistente(long estudioId) {
        Map<String, Object> row = jdbc.queryForMap("SELECT solicitado_em, lista_espera_ate, operacional_ate " +
                "FROM expurgos_estudio WHERE estudio_id = ?", estudioId);
        Instant solicitado = instant(row.get("solicitado_em"));
        Instant filaAte = instant(row.get("lista_espera_ate"));
        Instant operacionalAte = instant(row.get("operacional_ate"));
        manifesto.registrar(estudioId, solicitado, filaAte, operacionalAte);
        return new Resultado("ENCERRAMENTO_CONFIRMADO", solicitado, filaAte, operacionalAte, true);
    }

    public record ResultadoManifesto(int registros, int reconciliados) {}

    /** Applies signed closure intents after a rollback or before a restored database is released. */
    @PreAuthorize("hasRole('RETENTION_OPERATOR')")
    public ResultadoManifesto reconciliarManifestos() {
        return reconciliarManifestosInterno();
    }

    /** Startup-only entry point; the restore gate keeps tenant requests blocked during this call. */
    public ResultadoManifesto reconciliarManifestosNaInicializacao() {
        return reconciliarManifestosInterno();
    }

    private ResultadoManifesto reconciliarManifestosInterno() {
        try {
            var registros = manifesto.lerTodos();
            int reconciliados = 0;
            for (var tombstone : registros) {
                try {
                    Boolean alterado = transacaoReconciliacao.execute(status -> aplicarManifesto(tombstone));
                    if (Boolean.TRUE.equals(alterado)) reconciliados++;
                } catch (RuntimeException failure) {
                    log.error("Closure-manifest reconciliation failed; restore remains blocked. estudio_id={}",
                            tombstone.estudioId());
                    throw failure;
                }
            }
            reservarIdsEncerrados(registros);
            gateRestauracao.liberarAposReconciliacao();
            return new ResultadoManifesto(registros.size(), reconciliados);
        } catch (RuntimeException failure) {
            gateRestauracao.fecharAteReconciliacao();
            throw failure;
        }
    }

    /** Prevents a restored PostgreSQL identity sequence from assigning an old closed tenant ID again. */
    private void reservarIdsEncerrados(java.util.List<ManifestoEncerramentoStore.Tombstone> registros) {
        long maiorIdEncerrado = registros.stream()
                .mapToLong(ManifestoEncerramentoStore.Tombstone::estudioId).max().orElse(0L);
        if (maiorIdEncerrado == 0) return;

        Boolean postgres = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection -> {
            try {
                return "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
            } catch (java.sql.SQLException failure) {
                throw new IllegalStateException("Could not inspect database type during manifest replay.", failure);
            }
        });
        if (!Boolean.TRUE.equals(postgres)) return;

        String sequence = jdbc.queryForObject(
                "SELECT pg_get_serial_sequence('estudios', 'id')", String.class);
        if (sequence == null || !POSTGRES_SEQUENCE_NAME.matcher(sequence).matches())
            throw new IllegalStateException("Could not safely identify the estudio identity sequence.");

        Map<String, Object> current = jdbc.queryForMap("SELECT last_value, is_called FROM " + sequence);
        long lastValue = ((Number) current.get("last_value")).longValue();
        boolean alreadyCalled = (Boolean) current.get("is_called");
        long nextValue = alreadyCalled ? Math.addExact(lastValue, 1L) : lastValue;
        long requiredNext = Math.addExact(maiorIdEncerrado, 1L);
        if (requiredNext > nextValue)
            jdbc.queryForObject("SELECT setval(?::regclass, ?, false)", Long.class, sequence, requiredNext);
    }

    @Scheduled(fixedDelayString = "${marcae.retencao.manifesto-intervalo-ms:60000}")
    public void reconciliarManifestosAgendado() {
        if (!manifesto.configurado()) return;
        try {
            reconciliarManifestos();
        } catch (RuntimeException failure) {
            log.error("External closure manifest could not be reconciled; retention remains fail-closed.");
        }
    }

    private boolean aplicarManifesto(ManifestoEncerramentoStore.Tombstone tombstone) {
        long estudioId = tombstone.estudioId();
        Estudio estudio = estudios.bloquear(estudioId).orElse(null);
        if (estudio == null) return false;
        boolean alterado = !estudio.isEncerrado();
        if (alterado) {
            estudio.encerrar(tombstone.encerradoEm(), "closed-" + estudioId + "-" + tombstone.sequencia());
            estudios.saveAndFlush(estudio);
        }
        for (var usuario : usuarios.findByEstudioIdOrderByNomeAsc(estudioId)) {
            if (usuario.isAtivo()) {
                usuario.setAtivo(false);
                usuario.revogarSessoes();
                alterado = true;
            }
        }
        jdbc.update("DELETE FROM recuperacoes_senha WHERE usuario_id IN " +
                "(SELECT id FROM usuarios WHERE estudio_id = ?)", estudioId);
        tokens.excluirDoEstudio(estudioId);
        outbox.invalidateForStudio(estudioId);
        jdbc.update("UPDATE assinatura_checkouts SET checkout_url = NULL WHERE estudio_id = ?", estudioId);
        Integer jobs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM expurgos_estudio WHERE estudio_id = ?", Integer.class, estudioId);
        if (jobs == null || jobs == 0) {
            jdbc.update("INSERT INTO expurgos_estudio " +
                            "(estudio_id, solicitado_em, lista_espera_ate, operacional_ate) VALUES (?, ?, ?, ?)",
                    estudioId, utc(tombstone.encerradoEm()), utc(tombstone.listaEsperaAte()),
                    utc(tombstone.operacionalAte()));
            alterado = true;
        }
        return alterado;
    }

    private static java.time.OffsetDateTime utc(Instant value) {
        return java.time.OffsetDateTime.ofInstant(value, java.time.ZoneOffset.UTC);
    }

    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant valueInstant) return valueInstant;
        if (value instanceof java.time.OffsetDateTime valueOffset) return valueOffset.toInstant();
        if (value instanceof java.sql.Timestamp valueTimestamp) return valueTimestamp.toInstant();
        throw new IllegalStateException("Unsupported database timestamp type");
    }
}

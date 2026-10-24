package ink.manager.api.service;

import ink.manager.api.config.AcessoAtual;
import ink.manager.api.exception.ApiException;
import ink.manager.api.repository.EstudioRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.sql.PreparedStatement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tenant-scoped owner API for recording and reviewing exceptional financial holds. */
@Service
@Transactional
public class RetencaoFinanceiraService {
    private static final Duration MAX_REVIEW_INTERVAL = Duration.ofDays(90);

    public enum Tipo {
        OBRIGACAO_ESPECIFICA, PROCESSO_PENDENTE, FISCALIZACAO_PENDENTE
    }

    public record CriarPedido(
            @NotNull Long recebimentoId,
            @NotNull Tipo tipo,
            @NotBlank @Size(max = 1000) String justificativa,
            @NotBlank @Size(max = 1000) String escopo,
            @NotNull Instant revisarEm) {}

    public record RevisarPedido(@NotNull Instant revisarEm) {}

    public record EncerrarPedido(
            @NotBlank @Size(max = 20) @jakarta.validation.constraints.Pattern(regexp = "ENCERRAR") String confirmacao) {}

    public record Hold(
            long id, long recebimentoId, Tipo tipo, String justificativa, String escopo,
            Instant iniciadaEm, Instant revisarEm, Instant revisadaEm, Instant encerradaEm) {}

    public record Evento(long id, String acao, Instant ocorridoEm, Long atorUsuarioId,
            String atorRef, String nota) {}

    private final JdbcTemplate jdbc;
    private final EstudioRepository estudios;
    private final AcessoAtual acesso;
    private final Clock clock;

    public RetencaoFinanceiraService(
            JdbcTemplate jdbc, EstudioRepository estudios, AcessoAtual acesso, Clock clock) {
        this.jdbc = jdbc;
        this.estudios = estudios;
        this.acesso = acesso;
        this.clock = clock;
    }

    @PreAuthorize("hasRole('PROPRIETARIO')")
    public List<Hold> listar() {
        return listarParaEstudio(acesso.estudioId(), false);
    }

    @PreAuthorize("hasRole('RETENTION_OPERATOR')")
    public List<Hold> listarOperador(long estudioId) {
        return listarParaEstudio(estudioId, true);
    }

    private List<Hold> listarParaEstudio(long estudioId, boolean permitirEncerrado) {
        exigirEstudio(estudioId, permitirEncerrado);
        return jdbc.query("SELECT * FROM retencoes_legais_financeiras WHERE estudio_id = ? " +
                        "ORDER BY iniciada_em DESC, id DESC",
                (rs, row) -> hold(rs.getLong("id"), rs.getLong("recebimento_origem_id"),
                        rs.getString("tipo"), rs.getString("justificativa"), rs.getString("escopo"),
                        rs.getObject("iniciada_em"), rs.getObject("revisar_em"),
                        rs.getObject("revisada_em"), rs.getObject("encerrada_em")), estudioId);
    }

    @PreAuthorize("hasRole('PROPRIETARIO')")
    public Hold criar(CriarPedido pedido) {
        long actorId = acesso.usuarioId();
        return criarParaEstudio(acesso.estudioId(), pedido, actorId, "USUARIO:" + actorId, false);
    }

    @PreAuthorize("hasRole('RETENTION_OPERATOR')")
    public Hold criarOperador(long estudioId, CriarPedido pedido) {
        return criarParaEstudio(estudioId, pedido, null, operadorId(), true);
    }

    private Hold criarParaEstudio(long estudioId, CriarPedido pedido, Long actorId,
            String actorRef, boolean permitirEncerrado) {
        exigirEstudioComLock(estudioId, permitirEncerrado);
        Instant revisarEm = micros(pedido.revisarEm());
        validarRevisao(revisarEm);
        String justificativa = pedido.justificativa().trim();
        String escopo = pedido.escopo().trim();
        boolean recebimentoOriginal = jdbc.queryForObject(
                "SELECT COUNT(*) FROM recebimentos WHERE id = ? AND estudio_id = ?",
                Integer.class, pedido.recebimentoId(), estudioId) > 0;
        boolean recebimentoArquivado = jdbc.queryForObject(
                "SELECT COUNT(*) FROM recebimentos_arquivo WHERE recebimento_origem_id = ? AND estudio_id = ?",
                Integer.class, pedido.recebimentoId(), estudioId) > 0;
        if (!recebimentoOriginal && !recebimentoArquivado)
            throw new ApiException(HttpStatus.NOT_FOUND, "Recebimento nao encontrado neste estabelecimento.");

        List<Hold> existentes = jdbc.query("SELECT * FROM retencoes_legais_financeiras " +
                        "WHERE estudio_id = ? AND recebimento_origem_id = ? AND encerrada_em IS NULL " +
                        "ORDER BY id DESC FETCH FIRST 1 ROWS ONLY",
                (rs, row) -> hold(rs.getLong("id"), rs.getLong("recebimento_origem_id"),
                        rs.getString("tipo"), rs.getString("justificativa"), rs.getString("escopo"),
                        rs.getObject("iniciada_em"), rs.getObject("revisar_em"),
                        rs.getObject("revisada_em"), rs.getObject("encerrada_em")),
                estudioId, pedido.recebimentoId());
        if (!existentes.isEmpty()) {
            Hold existing = existentes.get(0);
            if (existing.tipo() == pedido.tipo()
                    && existing.justificativa().equals(justificativa)
                    && existing.escopo().equals(escopo)
                    && existing.revisarEm().equals(revisarEm))
                return existing;
            throw new ApiException(HttpStatus.CONFLICT, "Ja existe uma retencao ativa para esse recebimento.");
        }

        Instant agora = clock.instant();
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO retencoes_legais_financeiras " +
                            "(estudio_id, recebimento_origem_id, tipo, justificativa, escopo, iniciada_em, revisar_em) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    new String[] { "id" });
            statement.setLong(1, estudioId);
            statement.setLong(2, pedido.recebimentoId());
            statement.setString(3, pedido.tipo().name());
            statement.setString(4, justificativa);
            statement.setString(5, escopo);
            statement.setObject(6, utc(agora));
            statement.setObject(7, utc(revisarEm));
            return statement;
        }, keys);
        long holdId = generatedId(keys);
        registrarEvento(estudioId, holdId, actorId, actorRef, "CRIADO", agora, "Hold financeiro registrado.");
        return obter(estudioId, holdId);
    }

    @PreAuthorize("hasRole('PROPRIETARIO')")
    public Hold revisar(long holdId, RevisarPedido pedido) {
        long actorId = acesso.usuarioId();
        return revisarParaEstudio(acesso.estudioId(), holdId, pedido, actorId, "USUARIO:" + actorId, false);
    }

    @PreAuthorize("hasRole('RETENTION_OPERATOR')")
    public Hold revisarOperador(long estudioId, long holdId, RevisarPedido pedido) {
        return revisarParaEstudio(estudioId, holdId, pedido, null, operadorId(), true);
    }

    private Hold revisarParaEstudio(long estudioId, long holdId, RevisarPedido pedido,
            Long actorId, String actorRef, boolean permitirEncerrado) {
        exigirEstudioComLock(estudioId, permitirEncerrado);
        Instant revisarEm = micros(pedido.revisarEm());
        validarRevisao(revisarEm);
        Hold atual = obterComLock(estudioId, holdId);
        if (atual.encerradaEm() != null)
            throw new ApiException(HttpStatus.CONFLICT, "A retencao ja foi encerrada.");
        if (atual.revisarEm().equals(revisarEm)
                && atual.revisadaEm() != null)
            return atual;

        Instant agora = clock.instant();
        jdbc.update("UPDATE retencoes_legais_financeiras SET revisar_em = ?, revisada_em = ? " +
                        "WHERE estudio_id = ? AND id = ? AND encerrada_em IS NULL",
                utc(revisarEm), utc(agora), estudioId, holdId);
        registrarEvento(estudioId, holdId, actorId, actorRef, "REVISADO", agora, "Nova data de revisao registrada.");
        return obter(estudioId, holdId);
    }

    @PreAuthorize("hasRole('PROPRIETARIO')")
    public Hold encerrar(long holdId, EncerrarPedido pedido) {
        long actorId = acesso.usuarioId();
        return encerrarParaEstudio(acesso.estudioId(), holdId, pedido, actorId, "USUARIO:" + actorId, false);
    }

    @PreAuthorize("hasRole('RETENTION_OPERATOR')")
    public Hold encerrarOperador(long estudioId, long holdId, EncerrarPedido pedido) {
        return encerrarParaEstudio(estudioId, holdId, pedido, null, operadorId(), true);
    }

    private Hold encerrarParaEstudio(long estudioId, long holdId, EncerrarPedido pedido,
            Long actorId, String actorRef, boolean permitirEncerrado) {
        exigirEstudioComLock(estudioId, permitirEncerrado);
        Hold atual = obterComLock(estudioId, holdId);
        if (atual.encerradaEm() != null) return atual;

        Instant agora = clock.instant();
        jdbc.update("UPDATE retencoes_legais_financeiras SET encerrada_em = ? " +
                        "WHERE estudio_id = ? AND id = ? AND encerrada_em IS NULL",
                utc(agora), estudioId, holdId);
        registrarEvento(estudioId, holdId, actorId, actorRef, "ENCERRADO", agora,
                permitirEncerrado ? "Retencao legal encerrada pelo operador." : "Retencao legal encerrada pelo proprietario.");
        return obter(estudioId, holdId);
    }

    @PreAuthorize("hasRole('PROPRIETARIO')")
    @Transactional(readOnly = true)
    public List<Evento> historico(long holdId) {
        long estudioId = acesso.estudioId();
        return historicoParaEstudio(estudioId, holdId, false);
    }

    @PreAuthorize("hasRole('RETENTION_OPERATOR')")
    @Transactional(readOnly = true)
    public List<Evento> historicoOperador(long estudioId, long holdId) {
        return historicoParaEstudio(estudioId, holdId, true);
    }

    private List<Evento> historicoParaEstudio(long estudioId, long holdId, boolean permitirEncerrado) {
        exigirEstudio(estudioId, permitirEncerrado);
        obter(estudioId, holdId);
        return jdbc.query("SELECT id, acao, ocorrido_em, ator_usuario_id, nota " +
                        ", ator_ref FROM retencoes_legais_financeiras_eventos " +
                        "WHERE estudio_id = ? AND retencao_id = ? ORDER BY ocorrido_em, id",
                (rs, row) -> new Evento(rs.getLong("id"), rs.getString("acao"),
                        instant(rs.getObject("ocorrido_em")),
                        rs.getObject("ator_usuario_id") == null ? null : rs.getLong("ator_usuario_id"),
                        rs.getString("ator_ref"),
                        rs.getString("nota")), estudioId, holdId);
    }

    private Hold obter(long estudioId, long holdId) {
        List<Hold> rows = jdbc.query("SELECT * FROM retencoes_legais_financeiras " +
                        "WHERE estudio_id = ? AND id = ?",
                (rs, row) -> hold(rs.getLong("id"), rs.getLong("recebimento_origem_id"),
                        rs.getString("tipo"), rs.getString("justificativa"), rs.getString("escopo"),
                        rs.getObject("iniciada_em"), rs.getObject("revisar_em"),
                        rs.getObject("revisada_em"), rs.getObject("encerrada_em")), estudioId, holdId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "Retencao nao encontrada.");
        return rows.get(0);
    }

    private Hold obterComLock(long estudioId, long holdId) {
        List<Hold> rows = jdbc.query("SELECT * FROM retencoes_legais_financeiras " +
                        "WHERE estudio_id = ? AND id = ? FOR UPDATE",
                (rs, row) -> hold(rs.getLong("id"), rs.getLong("recebimento_origem_id"),
                        rs.getString("tipo"), rs.getString("justificativa"), rs.getString("escopo"),
                        rs.getObject("iniciada_em"), rs.getObject("revisar_em"),
                        rs.getObject("revisada_em"), rs.getObject("encerrada_em")), estudioId, holdId);
        if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "Retencao nao encontrada.");
        return rows.get(0);
    }

    private void registrarEvento(long estudioId, long holdId, Long actorId, String actorRef,
            String acao, Instant ocorreuEm, String nota) {
        jdbc.update("INSERT INTO retencoes_legais_financeiras_eventos " +
                        "(estudio_id, retencao_id, ator_usuario_id, ator_ref, acao, ocorrido_em, nota) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                estudioId, holdId, actorId, actorRef, acao, utc(ocorreuEm), nota);
    }

    private void exigirEstudio(long estudioId, boolean permitirEncerrado) {
        var estudio = estudios.findById(estudioId).orElseThrow();
        if (estudio.isEncerrado() && !permitirEncerrado)
            throw new ApiException(HttpStatus.CONFLICT, "O estabelecimento foi encerrado.");
    }

    private void exigirEstudioComLock(long estudioId, boolean permitirEncerrado) {
        var estudio = estudios.bloquear(estudioId).orElseThrow();
        if (estudio.isEncerrado() && !permitirEncerrado)
            throw new ApiException(HttpStatus.CONFLICT, "O estabelecimento foi encerrado.");
    }

    private static String operadorId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank())
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Operador de retencao nao autenticado.");
        return authentication.getName();
    }

    private void validarRevisao(Instant revisarEm) {
        Instant agora = clock.instant();
        if (!revisarEm.isAfter(agora) || revisarEm.isAfter(agora.plus(MAX_REVIEW_INTERVAL)))
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "A data de revisao deve estar no futuro e dentro de 90 dias.");
    }

    private static Instant micros(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }

    private static long generatedId(KeyHolder keys) {
        Map<String, Object> result = keys.getKeys();
        if (result == null || result.isEmpty())
            throw new IllegalStateException("Banco nao retornou o ID da retencao.");

        Object value = result.entrySet().stream()
                .filter(entry -> "id".equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        if (value == null && result.size() == 1)
            value = result.values().iterator().next();
        if (!(value instanceof Number key))
            throw new IllegalStateException("Banco nao retornou uma chave numerica para a retencao.");
        return key.longValue();
    }

    private static Hold hold(long id, long recebimentoId, String tipo, String justificativa,
            String escopo, Object iniciadaEm, Object revisarEm, Object revisadaEm, Object encerradaEm) {
        return new Hold(id, recebimentoId, Tipo.valueOf(tipo), justificativa, escopo,
                instant(iniciadaEm), instant(revisarEm), instant(revisadaEm), instant(encerradaEm));
    }

    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant result) return result;
        if (value instanceof OffsetDateTime result) return result.toInstant();
        if (value instanceof java.sql.Timestamp result) return result.toInstant();
        throw new IllegalStateException("Tipo de data inesperado no banco.");
    }

    private static OffsetDateTime utc(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }
}

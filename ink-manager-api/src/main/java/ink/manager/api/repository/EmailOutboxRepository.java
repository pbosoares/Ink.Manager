package ink.manager.api.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ink.manager.api.model.EmailOutbox;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, Long> {
    Optional<EmailOutbox> findByEstudioIdAndChaveIdempotencia(long estudioId, String chaveIdempotencia);

    @Query("select e.id from EmailOutbox e where " +
        "exists (select s.id from Estudio s where s.id = e.estudioId and s.encerradoEm is null) and " +
        "((e.estado = 'PENDENTE' and e.disponivelEm <= :agora) or " +
        "(e.estado = 'ENVIANDO' and e.leaseAte <= :agora)) " +
        "order by e.disponivelEm asc, e.id asc")
    List<Long> encontrarProntos(@Param("agora") Instant agora, Pageable pageable);

    @Modifying
    @Query("update EmailOutbox e set e.estado = 'ENVIANDO', e.leaseAte = :leaseAte, " +
        "e.leaseToken = :leaseToken, e.tentativas = e.tentativas + 1 " +
        "where e.id = :id and exists (select s.id from Estudio s where s.id = e.estudioId and s.encerradoEm is null) and " +
        "((e.estado = 'PENDENTE' and e.disponivelEm <= :agora) or " +
        "(e.estado = 'ENVIANDO' and e.leaseAte <= :agora))")
    int reclamar(@Param("id") long id, @Param("agora") Instant agora,
        @Param("leaseAte") Instant leaseAte, @Param("leaseToken") String leaseToken);

    @Modifying
    @Query("update EmailOutbox e set e.estado = 'ENVIADO', e.enviadoEm = :agora, " +
        "e.leaseAte = null, e.leaseToken = null, e.ultimoErro = null " +
        "where e.id = :id and e.estado = 'ENVIANDO' and e.leaseToken = :leaseToken")
    int marcarEnviado(@Param("id") long id, @Param("leaseToken") String leaseToken, @Param("agora") Instant agora);

    @Modifying
    @Query("update EmailOutbox e set e.estado = 'INVALIDADO', e.leaseAte = null, e.leaseToken = null " +
        "where e.id = :id and e.estado = 'ENVIANDO' and e.leaseToken = :leaseToken")
    int invalidarReivindicado(@Param("id") long id, @Param("leaseToken") String leaseToken);

    @Modifying
    @Query("update EmailOutbox e set e.estado = :estado, e.disponivelEm = :disponivelEm, " +
        "e.leaseAte = null, e.leaseToken = null, e.ultimoErro = :erro " +
        "where e.id = :id and e.estado = 'ENVIANDO' and e.leaseToken = :leaseToken")
    int registrarFalha(@Param("id") long id, @Param("leaseToken") String leaseToken,
        @Param("estado") String estado, @Param("disponivelEm") Instant disponivelEm, @Param("erro") String erro);

    @Modifying
    @Query("update EmailOutbox e set e.estado = 'INVALIDADO', e.leaseAte = null, e.leaseToken = null " +
        "where e.estudioId = :estudioId and e.tipo = :tipo and e.referencia = :referencia " +
        "and e.estado in ('PENDENTE','ENVIANDO')")
    int invalidar(@Param("estudioId") long estudioId, @Param("tipo") String tipo,
        @Param("referencia") String referencia);

    @Modifying
    @Query("update EmailOutbox e set e.estado = 'INVALIDADO', e.leaseAte = null, e.leaseToken = null " +
        "where e.estudioId = :estudioId and e.estado in ('PENDENTE','ENVIANDO')")
    int invalidarDoEstudio(@Param("estudioId") long estudioId);
}

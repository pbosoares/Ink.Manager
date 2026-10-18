package ink.manager.api.repository;
import ink.manager.api.model.Recebimento;
import java.util.*;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RecebimentoRepository extends JpaRepository<Recebimento,Long>{
    @org.springframework.data.jpa.repository.Query("select r.marcacao.id from Recebimento r where r.estudio.id = :estudioId")
    List<Long> marcacoesConfirmadas(Long estudioId);
    Optional<Recebimento> findByMarcacaoIdAndEstudioId(Long marcacaoId,Long estudioId);
    boolean existsByMarcacaoIdAndEstudioId(Long marcacaoId,Long estudioId);
    List<Recebimento> findByEstudioIdAndConfirmadoEmGreaterThanEqualAndConfirmadoEmLessThanOrderByConfirmadoEmDesc(Long estudioId,Instant inicio,Instant fim);
    @org.springframework.data.jpa.repository.Query("select r.valor from Recebimento r where r.estudio.id=:estudio and r.confirmadoEm>=:inicio and r.confirmadoEm<:fim")
    List<java.math.BigDecimal> valoresNoPeriodo(@org.springframework.data.repository.query.Param("estudio") Long estudio,
            @org.springframework.data.repository.query.Param("inicio") Instant inicio,
            @org.springframework.data.repository.query.Param("fim") Instant fim);
}

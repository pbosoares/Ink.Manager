package ink.manager.api.repository;

import ink.manager.api.model.*;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OfertaListaEsperaRepository extends JpaRepository<OfertaListaEspera,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OfertaListaEspera o where o.estudio.id=:estudio and o.id=:id")
    Optional<OfertaListaEspera> bloquearPorEstudio(@Param("estudio") Long estudio,@Param("id") Long id);
    Optional<OfertaListaEspera> findByEstudioIdAndTokenHash(Long estudio,String tokenHash);
    List<OfertaListaEspera> findByEstudioIdAndEntradaIdOrderByCriadoEmDesc(Long estudio,Long entrada);
    @Query("select o from OfertaListaEspera o where o.estado=:estado and o.tokenPrazo<=:limite and o.estudio.encerradoEm is null")
    List<OfertaListaEspera> encontrarExpiradasEmEstudiosAbertos(@Param("estado") EstadoOfertaListaEspera estado,
            @Param("limite") java.time.Instant limite);
    boolean existsByEstudioIdAndMarcacaoCanceladaIdAndEstado(Long estudio,Long marcacaoCancelada,EstadoOfertaListaEspera estado);
}

package ink.manager.api.repository;

import ink.manager.api.model.*;
import java.time.OffsetDateTime;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ListaEsperaRepository extends JpaRepository<ListaEspera,Long> {
    Optional<ListaEspera> findByEstudioIdAndChaveIdempotencia(Long estudioId,UUID chaveIdempotencia);
    List<ListaEspera> findByEstudioIdOrderByCriadoEmAscIdAsc(Long estudioId);
    Optional<ListaEspera> findByIdAndEstudioId(Long id,Long estudioId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from ListaEspera l where l.estudio.id=:estudio and l.servico.id=:servico and l.estado='AGUARDANDO' and l.inicioDesejado<=:instante and l.fimDesejado>:instante and (l.profissional is null or l.profissional.id=:profissional) order by l.criadoEm,l.id")
    List<ListaEspera> candidatas(@Param("estudio") Long estudio,@Param("servico") Long servico,@Param("profissional") Long profissional,@Param("instante") OffsetDateTime instante);
}

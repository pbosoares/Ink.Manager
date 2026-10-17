package ink.manager.api.repository;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import ink.manager.api.model.Estudio;
import org.springframework.data.jpa.repository.*;
public interface EstudioRepository extends JpaRepository<Estudio, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Estudio e where e.id = :id")
    @QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000"))
    Optional<Estudio> bloquear(Long id);
}

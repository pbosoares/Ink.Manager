package ink.manager.api.repository;
import ink.manager.api.model.AgendaLock;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
public interface AgendaLockRepository extends JpaRepository<AgendaLock,Integer> {
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @QueryHints(@jakarta.persistence.QueryHint(name="jakarta.persistence.lock.timeout", value="10000"))
 @Query("select a from AgendaLock a where a.id = 1")
 AgendaLock lockAgenda();
}

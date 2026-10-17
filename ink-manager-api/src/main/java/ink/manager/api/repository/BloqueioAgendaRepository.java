package ink.manager.api.repository;
import java.util.*;
import ink.manager.api.model.BloqueioAgenda;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BloqueioAgendaRepository extends JpaRepository<BloqueioAgenda,Long>{
    List<BloqueioAgenda> findByProfissionalIdOrderByInicioAsc(Long profissionalId);
    Optional<BloqueioAgenda> findByIdAndProfissionalId(Long id,Long profissionalId);
}

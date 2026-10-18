package ink.manager.api.repository;
import ink.manager.api.model.Servico;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ServicoRepository extends JpaRepository<Servico,Long>{
    List<Servico> findByEstudioIdOrderByNomeAsc(Long estudioId);
    Optional<Servico> findByIdAndEstudioId(Long id,Long estudioId);
}

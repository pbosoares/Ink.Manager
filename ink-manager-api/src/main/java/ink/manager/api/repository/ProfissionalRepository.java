package ink.manager.api.repository;
import java.util.*;
import ink.manager.api.model.Profissional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProfissionalRepository extends JpaRepository<Profissional,Long>{
    List<Profissional> findByEstudioId(Long estudioId);
    Optional<Profissional> findByIdAndEstudioId(Long id,Long estudioId);
}

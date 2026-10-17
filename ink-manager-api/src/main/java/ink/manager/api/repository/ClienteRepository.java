package ink.manager.api.repository;
import java.util.*;
import ink.manager.api.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    List<Cliente> findByEstudioId(Long estudioId);
    Optional<Cliente> findByIdAndEstudioId(Long id, Long estudioId);
}

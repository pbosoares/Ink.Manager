package ink.manager.api.repository;
import java.util.*;
import ink.manager.api.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmailIgnoreCase(String email);
    Optional<Usuario> findByIdAndEstudioId(Long id, Long estudioId);
    List<Usuario> findByEstudioIdOrderByNomeAsc(Long estudioId);
}

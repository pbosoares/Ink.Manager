package ink.manager.api.repository;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import ink.manager.api.model.RecuperacaoSenha;
public interface RecuperacaoSenhaRepository extends JpaRepository<RecuperacaoSenha,String>{
    void deleteByUsuarioId(Long usuarioId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RecuperacaoSenha r where r.tokenHash = :hash")
    Optional<RecuperacaoSenha> bloquear(String hash);
}

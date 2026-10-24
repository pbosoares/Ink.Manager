package ink.manager.api.repository;

import ink.manager.api.model.EmailActionToken;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailActionTokenRepository extends JpaRepository<EmailActionToken,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from EmailActionToken t where t.tokenHash=:hash")
    Optional<EmailActionToken> bloquear(@Param("hash") String hash);
    @Modifying
    @Query("delete from EmailActionToken t where t.estudioId=:estudio and t.marcacaoId=:marcacao and t.usadoEm is null")
    int invalidarNaoUsados(@Param("estudio") long estudioId,@Param("marcacao") long marcacaoId);
    @Modifying
    @Query("delete from EmailActionToken t where t.estudioId=:estudio")
    int excluirDoEstudio(@Param("estudio") long estudioId);
}

package ink.manager.api.repository;
import java.time.LocalDate;
import java.util.*;
import ink.manager.api.model.Marcacao;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MarcacaoRepository extends JpaRepository<Marcacao,Long>{
    List<Marcacao> findByEstudioId(Long estudioId);
    Optional<Marcacao> findByIdAndEstudioId(Long id,Long estudioId);
    List<Marcacao> findByProfissionalIdAndEstudioId(Long profissionalId,Long estudioId);
    List<Marcacao> findByProfissionalIdAndEstudioIdAndDataBetween(Long profissionalId,Long estudioId,LocalDate inicio,LocalDate fim);
    boolean existsByIdAndProfissionalIdAndEstudioId(Long id,Long profissionalId,Long estudioId);
}

package ink.manager.api.repository;
import java.util.List;
import ink.manager.api.model.HorarioTrabalho;
import org.springframework.data.jpa.repository.JpaRepository;
public interface HorarioTrabalhoRepository extends JpaRepository<HorarioTrabalho,Long>{
    List<HorarioTrabalho> findByProfissionalIdOrderByDiaSemanaAscInicioAsc(Long profissionalId);
    void deleteByProfissionalId(Long profissionalId);
}

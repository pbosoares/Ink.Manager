package ink.manager.api.repository;
import java.util.Collection;
import java.util.List;
import java.time.LocalTime;
import ink.manager.api.model.HorarioTrabalho;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface HorarioTrabalhoRepository extends JpaRepository<HorarioTrabalho,Long>{
    interface HorarioProfissionalProjection{
        Long getProfissionalId();
        Integer getDiaSemana();
        LocalTime getInicio();
        LocalTime getFim();
    }
    List<HorarioTrabalho> findByProfissionalIdOrderByDiaSemanaAscInicioAsc(Long profissionalId);
    @Query("select h.profissional.id as profissionalId,h.diaSemana as diaSemana,h.inicio as inicio,h.fim as fim from HorarioTrabalho h where h.profissional.estudio.id=:estudioId and h.profissional.id in :profissionalIds order by h.profissional.id asc,h.diaSemana asc,h.inicio asc,h.id asc")
    List<HorarioProfissionalProjection> listarPorEstudioEProfissionaisOrdenado(@Param("estudioId") Long estudioId,@Param("profissionalIds") Collection<Long> profissionalIds);
    void deleteByProfissionalId(Long profissionalId);
}

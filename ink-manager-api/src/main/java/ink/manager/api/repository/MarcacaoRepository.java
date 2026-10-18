package ink.manager.api.repository;
import java.time.LocalDate;
import java.util.*;
import ink.manager.api.model.Marcacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ink.manager.api.dto.LinhaRelatorioAgendamento;
import ink.manager.api.dto.RegistroFalta;
public interface MarcacaoRepository extends JpaRepository<Marcacao,Long>{
    Optional<Marcacao> findByEstudioIdAndChavePublica(Long estudioId,String chavePublica);
    List<Marcacao> findByEstudioIdOrderByDataAscHorarioAscIdAsc(Long estudioId);
    List<Marcacao> findByEstudioId(Long estudioId);
    Optional<Marcacao> findByIdAndEstudioId(Long id,Long estudioId);
    List<Marcacao> findByProfissionalIdAndEstudioId(Long profissionalId,Long estudioId);
    List<Marcacao> findByProfissionalIdAndEstudioIdAndDataBetween(Long profissionalId,Long estudioId,LocalDate inicio,LocalDate fim);
    boolean existsByIdAndProfissionalIdAndEstudioId(Long id,Long profissionalId,Long estudioId);
    @Query("select count(m) from Marcacao m join m.cliente c where m.estudio.id=:estudio and lower(c.email)=:email and m.status in (ink.manager.api.model.StatusMarcacao.AGENDADA,ink.manager.api.model.StatusMarcacao.CONFIRMADA) and (m.data>:hoje or (m.data=:hoje and m.horario>=:agora))")
    long contarAgendamentosAbertosPorEmail(@Param("estudio") Long estudio,@Param("email") String email,@Param("hoje") LocalDate hoje,@Param("agora") java.time.LocalTime agora);
    @Query("select new ink.manager.api.dto.LinhaRelatorioAgendamento(m.data,m.status,m.presenca,m.precoPrevisto,m.servicoNome,s.id) from Marcacao m left join m.servico s where m.estudio.id=:estudio and m.data>=:inicio and m.data<:fim order by m.data,m.horario,m.id")
    List<LinhaRelatorioAgendamento> relatorioMensal(@Param("estudio") Long estudio,@Param("inicio") LocalDate inicio,@Param("fim") LocalDate fim);
    @Query("select new ink.manager.api.dto.RegistroFalta(m.data,m.horario,m.servicoNome,m.profissional.nome) from Marcacao m where m.estudio.id=:estudio and m.cliente.id=:cliente and m.presenca='FALTOU' order by m.data desc,m.horario desc")
    List<RegistroFalta> historicoFaltas(@Param("estudio") Long estudio,@Param("cliente") Long cliente);
    @Query("select new ink.manager.api.dto.RegistroFalta(m.data,m.horario,m.servicoNome,m.profissional.nome) from Marcacao m where m.estudio.id=:estudio and lower(m.cliente.email)=:email and m.presenca='FALTOU' order by m.data desc,m.horario desc")
    List<RegistroFalta> historicoFaltasEmail(@Param("estudio") Long estudio,@Param("email") String email);
}

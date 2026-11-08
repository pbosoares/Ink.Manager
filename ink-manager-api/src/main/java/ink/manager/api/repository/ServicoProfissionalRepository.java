package ink.manager.api.repository;

import ink.manager.api.model.ServicoProfissional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServicoProfissionalRepository extends JpaRepository<ServicoProfissional, Long> {
    @Query("select vinculo.profissional.id from ServicoProfissional vinculo "
            + "where vinculo.estudioId = :estudioId and vinculo.servico.id = :servicoId "
            + "order by vinculo.profissional.id")
    List<Long> listarProfissionalIds(@Param("estudioId") Long estudioId, @Param("servicoId") Long servicoId);

    @Query("select case when count(vinculo) > 0 then true else false end from ServicoProfissional vinculo "
            + "where vinculo.estudioId = :estudioId and vinculo.servico.id = :servicoId "
            + "and vinculo.profissional.id = :profissionalId")
    boolean existeVinculo(@Param("estudioId") Long estudioId, @Param("servicoId") Long servicoId,
            @Param("profissionalId") Long profissionalId);

    @Modifying
    @Query("delete from ServicoProfissional vinculo where vinculo.estudioId = :estudioId "
            + "and vinculo.servico.id = :servicoId")
    int removerVinculos(@Param("estudioId") Long estudioId, @Param("servicoId") Long servicoId);
}

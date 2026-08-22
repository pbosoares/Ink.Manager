package ink.manager.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ink.manager.api.model.Marcacao;

@Repository
public interface MarcacaoRepository extends JpaRepository<Marcacao, Long> {

}
package ink.manager.api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ink.manager.api.model.Cliente;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {
}
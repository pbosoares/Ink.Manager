package ink.manager.api.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import ink.manager.api.exception.ApiException;

import ink.manager.api.model.Cliente;
import ink.manager.api.repository.ClienteRepository;

@Service
@org.springframework.transaction.annotation.Transactional
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final ink.manager.api.config.AcessoAtual acesso;
    private final ink.manager.api.repository.EstudioRepository estudios;

    @Autowired
    public ClienteService(ClienteRepository clienteRepository, ink.manager.api.config.AcessoAtual acesso, ink.manager.api.repository.EstudioRepository estudios) {
        this.clienteRepository = clienteRepository;
        this.acesso = acesso; this.estudios = estudios;
    }

    public List<Cliente> listarTodos() {
        return clienteRepository.findByEstudioId(acesso.estudioId());
    }

    public Optional<Cliente> buscarPorId(Long id) {
        return clienteRepository.findByIdAndEstudioId(id, acesso.estudioId());
    }

    public Cliente salvar(Cliente cliente) {
        cliente.setEstudio(estudios.getReferenceById(acesso.estudioId()));
        cliente.setId(null);
        cliente.setVersion(null);
        return clienteRepository.saveAndFlush(cliente);
    }

    public Cliente atualizar(Long id, Cliente clienteAtualizado) {
        Cliente cliente = clienteRepository.findByIdAndEstudioId(id, acesso.estudioId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Cliente não encontrado com id: " + id));

        if (clienteAtualizado.getVersion() == null || !java.util.Objects.equals(cliente.getVersion(), clienteAtualizado.getVersion())) {
            throw new ApiException(HttpStatus.CONFLICT, "Registro alterado. Recarregue os dados antes de salvar.");
        }
        cliente.setNome(clienteAtualizado.getNome());
        cliente.setTelefone(clienteAtualizado.getTelefone());
        cliente.setIdade(clienteAtualizado.getIdade());
        cliente.setInstagram(clienteAtualizado.getInstagram());

        return clienteRepository.saveAndFlush(cliente);
    }

    public void deletar(Long id) {
        Cliente cliente = clienteRepository.findByIdAndEstudioId(id, acesso.estudioId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Cliente não encontrado com id: " + id));
        clienteRepository.delete(cliente);
    }
}
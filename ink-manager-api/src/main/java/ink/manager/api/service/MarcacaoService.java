package ink.manager.api.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ink.manager.api.dto.MarcacaoComClienteRequest;
import org.springframework.http.HttpStatus;
import ink.manager.api.exception.ApiException;

import ink.manager.api.model.Cliente;
import ink.manager.api.model.Marcacao;
import ink.manager.api.repository.ClienteRepository;
import ink.manager.api.repository.MarcacaoRepository;

@Service
public class MarcacaoService {

    private final MarcacaoRepository marcacaoRepository;
    private final ClienteRepository clienteRepository;

    public MarcacaoService(
            MarcacaoRepository marcacaoRepository,
            ClienteRepository clienteRepository) {

        this.marcacaoRepository = marcacaoRepository;
        this.clienteRepository = clienteRepository;
    }

    public List<Marcacao> listarTodas() {
        return marcacaoRepository.findAll();
    }

    public Marcacao buscarPorId(Long id) {
        return marcacaoRepository.findById(id)
                .orElseThrow(() ->
                        new ApiException(HttpStatus.NOT_FOUND, "Marcação não encontrada com id: " + id));
    }

    public Marcacao salvar(Marcacao marcacao) {

        marcacao.setId(null);
        Long clienteId = validarCliente(marcacao);

        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() ->
                        new ApiException(HttpStatus.NOT_FOUND, "Cliente não encontrado com id: " + clienteId));

        marcacao.setCliente(cliente);

        return marcacaoRepository.save(marcacao);
    }

    // Both writes commit together; a failed appointment must not leave an orphan client.
    @Transactional
    public Marcacao salvarComCliente(Long id, MarcacaoComClienteRequest request) {
        if (id != null) buscarPorId(id);
        Cliente cliente = request.cliente();
        cliente.setId(null);
        cliente = clienteRepository.save(cliente);
        Marcacao marcacao = new Marcacao(cliente, request.data(), request.horario(),
                request.descricao(), request.status());
        return id == null ? salvar(marcacao) : atualizar(id, marcacao);
    }

    public Marcacao atualizar(Long id, Marcacao marcacaoAtualizada) {

        Marcacao marcacao = marcacaoRepository.findById(id)
                .orElseThrow(() ->
                        new ApiException(HttpStatus.NOT_FOUND, "Marcação não encontrada com id: " + id));

        Long clienteId = validarCliente(marcacaoAtualizada);

        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() ->
                        new ApiException(HttpStatus.NOT_FOUND, "Cliente não encontrado com id: " + clienteId));

        marcacao.setCliente(cliente);
        marcacao.setData(marcacaoAtualizada.getData());
        marcacao.setHorario(marcacaoAtualizada.getHorario());
        marcacao.setDescricao(marcacaoAtualizada.getDescricao());
        marcacao.setStatus(marcacaoAtualizada.getStatus());

        return marcacaoRepository.save(marcacao);
    }

    private Long validarCliente(Marcacao marcacao) {
        if (marcacao.getCliente() == null || marcacao.getCliente().getId() == null
                || marcacao.getCliente().getId() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Informe um cliente válido.");
        }
        return marcacao.getCliente().getId();
    }

    public void deletar(Long id) {
        marcacaoRepository.delete(buscarPorId(id));
    }
}

package ink.manager.api.service;

import java.util.List;

import org.springframework.stereotype.Service;

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
                        new RuntimeException("Marcação não encontrada com id: " + id));
    }

    public Marcacao salvar(Marcacao marcacao) {

        Long clienteId = marcacao.getCliente().getId();

        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() ->
                        new RuntimeException("Cliente não encontrado com id: " + clienteId));

        marcacao.setCliente(cliente);

        return marcacaoRepository.save(marcacao);
    }

    public Marcacao atualizar(Long id, Marcacao marcacaoAtualizada) {

        Marcacao marcacao = marcacaoRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Marcação não encontrada com id: " + id));

        Long clienteId = marcacaoAtualizada.getCliente().getId();

        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() ->
                        new RuntimeException("Cliente não encontrado com id: " + clienteId));

        marcacao.setCliente(cliente);
        marcacao.setData(marcacaoAtualizada.getData());
        marcacao.setHorario(marcacaoAtualizada.getHorario());
        marcacao.setDescricao(marcacaoAtualizada.getDescricao());
        marcacao.setStatus(marcacaoAtualizada.getStatus());

        return marcacaoRepository.save(marcacao);
    }

    public void deletar(Long id) {
        marcacaoRepository.deleteById(id);
    }
}
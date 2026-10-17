package ink.manager.api.service;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import ink.manager.api.model.Cliente;
import ink.manager.api.repository.ClienteRepository;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;

    @Mock private ink.manager.api.config.AcessoAtual acesso;
    @Mock private ink.manager.api.repository.EstudioRepository estudios;
    @org.junit.jupiter.api.BeforeEach void contexto(){
        org.mockito.Mockito.lenient().when(acesso.estudioId()).thenReturn(1L);
        org.mockito.Mockito.lenient().when(estudios.getReferenceById(1L)).thenReturn(new ink.manager.api.model.Estudio());
    }

    @InjectMocks
    private ClienteService clienteService;

    @Test
    void deveListarTodosOsClientes() {

        Cliente cliente1 = criarCliente(
                "Pablo",
                "21999999999",
                32,
                "@pablo"
        );

        Cliente cliente2 = criarCliente(
                "Maria",
                "21888888888",
                28,
                "@maria"
        );

        when(clienteRepository.findByEstudioId(1L))
                .thenReturn(List.of(cliente1, cliente2));

        List<Cliente> resultado = clienteService.listarTodos();

        assertEquals(2, resultado.size());
        assertEquals("Pablo", resultado.get(0).getNome());
        assertEquals("Maria", resultado.get(1).getNome());

        verify(clienteRepository).findByEstudioId(1L);
    }

    @Test
    void deveBuscarClientePorId() {

        Cliente cliente = criarCliente(
                "Pablo",
                "21999999999",
                32,
                "@pablo"
        );

        when(clienteRepository.findByIdAndEstudioId(1L, 1L))
                .thenReturn(Optional.of(cliente));

        Optional<Cliente> resultado =
                clienteService.buscarPorId(1L);

        assertTrue(resultado.isPresent());
        assertSame(cliente, resultado.get());

        verify(clienteRepository).findByIdAndEstudioId(1L, 1L);
    }

    @Test
    void deveRetornarVazioQuandoClienteNaoExistir() {

        when(clienteRepository.findByIdAndEstudioId(99L, 1L))
                .thenReturn(Optional.empty());

        Optional<Cliente> resultado =
                clienteService.buscarPorId(99L);

        assertTrue(resultado.isEmpty());

        verify(clienteRepository).findByIdAndEstudioId(99L, 1L);
    }

    @Test
    void deveSalvarCliente() {

        Cliente cliente = criarCliente(
                "Pablo",
                "21999999999",
                32,
                "@pablo"
        );

        when(clienteRepository.saveAndFlush(cliente))
                .thenReturn(cliente);

        Cliente resultado =
                clienteService.salvar(cliente);

        assertSame(cliente, resultado);

        verify(clienteRepository).saveAndFlush(cliente);
    }

    @Test
    void deveAtualizarCliente() {

        Cliente clienteExistente = criarCliente(
                "Pablo",
                "21999999999",
                32,
                "@pablo"
        );

        Cliente clienteAtualizado = criarCliente(
                "Pablo Soares",
                "21777777777",
                33,
                "@pablosoares"
        );

        when(clienteRepository.findByIdAndEstudioId(1L, 1L))
                .thenReturn(Optional.of(clienteExistente));

        when(clienteRepository.saveAndFlush(clienteExistente))
                .thenReturn(clienteExistente);

        Cliente resultado =
                clienteService.atualizar(1L, clienteAtualizado);

        assertEquals("Pablo Soares", resultado.getNome());
        assertEquals("21777777777", resultado.getTelefone());
        assertEquals(33, resultado.getIdade());
        assertEquals("@pablosoares", resultado.getInstagram());

        verify(clienteRepository).findByIdAndEstudioId(1L, 1L);
        verify(clienteRepository).saveAndFlush(clienteExistente);
    }

    @Test
    void deveLancarExcecaoAoAtualizarClienteInexistente() {

        when(clienteRepository.findByIdAndEstudioId(99L, 1L))
                .thenReturn(Optional.empty());

        Cliente clienteAtualizado = criarCliente(
                "Cliente",
                "21999999999",
                30,
                "@cliente"
        );

        RuntimeException excecao = assertThrows(
                RuntimeException.class,
                () -> clienteService.atualizar(99L, clienteAtualizado)
        );

        assertEquals(
                "Cliente não encontrado com id: 99",
                excecao.getMessage()
        );

        verify(clienteRepository).findByIdAndEstudioId(99L, 1L);
        verify(clienteRepository, never()).saveAndFlush(any(Cliente.class));
    }

    @Test
    void deveDeletarCliente() {
        Cliente cliente = criarCliente("Pablo", "21999999999", 32, "@pablo");
        when(clienteRepository.findByIdAndEstudioId(1L, 1L)).thenReturn(Optional.of(cliente));
        clienteService.deletar(1L);

        verify(clienteRepository).delete(cliente);
    }

    @Test
    void naoDeveDeletarClienteInexistente() {
        assertThrows(ink.manager.api.exception.ApiException.class, () -> clienteService.deletar(99L));
        verify(clienteRepository, never()).delete(any(Cliente.class));
    }

    private Cliente criarCliente(
            String nome,
            String telefone,
            int idade,
            String instagram
    ) {

        Cliente cliente = new Cliente();

        cliente.setVersion(0L);
        cliente.setNome(nome);
        cliente.setTelefone(telefone);
        cliente.setIdade(idade);
        cliente.setInstagram(instagram);

        return cliente;
    }
}

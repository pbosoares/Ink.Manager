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

        when(clienteRepository.findAll())
                .thenReturn(List.of(cliente1, cliente2));

        List<Cliente> resultado = clienteService.listarTodos();

        assertEquals(2, resultado.size());
        assertEquals("Pablo", resultado.get(0).getNome());
        assertEquals("Maria", resultado.get(1).getNome());

        verify(clienteRepository).findAll();
    }

    @Test
    void deveBuscarClientePorId() {

        Cliente cliente = criarCliente(
                "Pablo",
                "21999999999",
                32,
                "@pablo"
        );

        when(clienteRepository.findById(1L))
                .thenReturn(Optional.of(cliente));

        Optional<Cliente> resultado =
                clienteService.buscarPorId(1L);

        assertTrue(resultado.isPresent());
        assertSame(cliente, resultado.get());

        verify(clienteRepository).findById(1L);
    }

    @Test
    void deveRetornarVazioQuandoClienteNaoExistir() {

        when(clienteRepository.findById(99L))
                .thenReturn(Optional.empty());

        Optional<Cliente> resultado =
                clienteService.buscarPorId(99L);

        assertTrue(resultado.isEmpty());

        verify(clienteRepository).findById(99L);
    }

    @Test
    void deveSalvarCliente() {

        Cliente cliente = criarCliente(
                "Pablo",
                "21999999999",
                32,
                "@pablo"
        );

        when(clienteRepository.save(cliente))
                .thenReturn(cliente);

        Cliente resultado =
                clienteService.salvar(cliente);

        assertSame(cliente, resultado);

        verify(clienteRepository).save(cliente);
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

        when(clienteRepository.findById(1L))
                .thenReturn(Optional.of(clienteExistente));

        when(clienteRepository.save(clienteExistente))
                .thenReturn(clienteExistente);

        Cliente resultado =
                clienteService.atualizar(1L, clienteAtualizado);

        assertEquals("Pablo Soares", resultado.getNome());
        assertEquals("21777777777", resultado.getTelefone());
        assertEquals(33, resultado.getIdade());
        assertEquals("@pablosoares", resultado.getInstagram());

        verify(clienteRepository).findById(1L);
        verify(clienteRepository).save(clienteExistente);
    }

    @Test
    void deveLancarExcecaoAoAtualizarClienteInexistente() {

        when(clienteRepository.findById(99L))
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

        verify(clienteRepository).findById(99L);
        verify(clienteRepository, never()).save(any(Cliente.class));
    }

    @Test
    void deveDeletarCliente() {

        clienteService.deletar(1L);

        verify(clienteRepository).deleteById(1L);
    }

    private Cliente criarCliente(
            String nome,
            String telefone,
            int idade,
            String instagram
    ) {

        Cliente cliente = new Cliente();

        cliente.setNome(nome);
        cliente.setTelefone(telefone);
        cliente.setIdade(idade);
        cliente.setInstagram(instagram);

        return cliente;
    }
}
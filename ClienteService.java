import java.util.List;

public class ClienteService {

    private ClienteRepository repository;

    public ClienteService() {
        repository = new ClienteRepository();
    }

    public Cliente buscarClientePorId(int id) {
        return repository.buscarPorId(id);
    }

    public boolean removerCliente(int id) {
        return repository.removerPorId(id);
    }

    public boolean atualizarCliente(int id, String nome, String telefone,
                                    int idade, String instagram) {

        Cliente cliente = new Cliente();

        cliente.setId(id);
        cliente.setNome(nome);
        cliente.setTelefone(telefone);
        cliente.setIdade(idade);
        cliente.setInstagram(instagram);

        return repository.atualizar(cliente);
    }

    public void cadastrarCliente(Cliente cliente) {
        repository.salvar(cliente);
    }

    public void listarClientes() {

        List<Cliente> clientes = repository.listarTodos();

        for (Cliente cliente : clientes) {

            System.out.println("-----------------------");
            System.out.println("ID: " + cliente.getId());
            System.out.println("Nome: " + cliente.getNome());
            System.out.println("Telefone: " + cliente.getTelefone());
            System.out.println("Idade: " + cliente.getIdade());
            System.out.println("Instagram: " + cliente.getInstagram());
        }
    }
}
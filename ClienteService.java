import java.util.ArrayList;

public class ClienteService {
private ArrayList<Cliente> clientes;
private int proximoId = 1;

public ClienteService() {
	clientes = new ArrayList<>();

}

public Cliente buscarClientePorId(int id) {

     for (Cliente cliente : clientes) {
        if (id == cliente.getId()) {
            return cliente;
        }
    }

    return null;

}

    public boolean removerCliente(int id) {
    return clientes.removeIf(cliente -> cliente.getId() == id);
}

    public boolean atualizarCliente(int id, String nome, String telefone, int idade, String instagram) {

    Cliente cliente = buscarClientePorId(id);

    if (cliente != null) {
        cliente.setNome(nome);
        cliente.setTelefone(telefone);
        cliente.setIdade(idade);
        cliente.setInstagram(instagram);

        return true;
    }

    return false;
}

	public void cadastrarCliente(Cliente cliente) {

    cliente.setId(proximoId);
    proximoId++;

    clientes.add(cliente);
}

	public void listarClientes() {

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

	

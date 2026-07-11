import java.util.ArrayList;

public class ClienteService {
private ArrayList<Cliente> clientes;

public ClienteService() {
	clientes = new ArrayList<>();

}

public void cadastrarCliente(Cliente cliente) {
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

	

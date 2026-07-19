import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class ClienteService {

private ArrayList<Cliente> clientes;
private int proximoId = 1;

private final String ARQUIVO = "clientes.txt";

public ClienteService() {
    clientes = new ArrayList<>();
    carregarClientes();
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

    boolean removido = clientes.removeIf(cliente -> cliente.getId() == id);

    if (removido) {
        salvarClientes();
    }

    return removido;
}

public boolean atualizarCliente(int id, String nome, String telefone,
                                int idade, String instagram) {

    Cliente cliente = buscarClientePorId(id);

    if (cliente != null) {

        cliente.setNome(nome);
        cliente.setTelefone(telefone);
        cliente.setIdade(idade);
        cliente.setInstagram(instagram);

        salvarClientes();

        return true;
    }

    return false;
}

public void cadastrarCliente(Cliente cliente) {

    cliente.setId(proximoId);
    proximoId++;

    clientes.add(cliente);

    salvarClientes();
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

private void salvarClientes() {

    try (BufferedWriter writer = new BufferedWriter(new FileWriter(ARQUIVO))) {

        for (Cliente cliente : clientes) {

            writer.write(
                cliente.getId() + ";" +
                cliente.getNome() + ";" +
                cliente.getTelefone() + ";" +
                cliente.getIdade() + ";" +
                cliente.getInstagram()
            );

            writer.newLine();
        }

    } catch (IOException e) {

        System.out.println("Erro ao salvar os clientes.");
    }
}

private void carregarClientes() {

    try (BufferedReader reader = new BufferedReader(new FileReader(ARQUIVO))) {

        String linha;

        while ((linha = reader.readLine()) != null) {

            String[] dados = linha.split(";");

            Cliente cliente = new Cliente();

            cliente.setId(Integer.parseInt(dados[0]));
            cliente.setNome(dados[1]);
            cliente.setTelefone(dados[2]);
            cliente.setIdade(Integer.parseInt(dados[3]));
            cliente.setInstagram(dados[4]);

            clientes.add(cliente);

            if (cliente.getId() >= proximoId) {
                proximoId = cliente.getId() + 1;
            }
        }

    } catch (IOException e) {

        System.out.println("Nenhum arquivo de clientes encontrado. Iniciando sistema vazio.");
    }
}


}

import java.util.Scanner;

public class MainMaster {
	
	public static void main(String[] args) {

		Scanner scanner = new Scanner(System.in);
		ClienteService service = new ClienteService();

		int opcao;

		do {

			System.out.println("\n===== INK.MANAGER ===== ");
			System.out.println("1 - Cadastrar cliente");
			System.out.println("2 - Listar clientes");
			System.out.println("3 - Buscar cliente");
			System.out.println("0 - Sair");
			System.out.println("Escolha uma opção: ");

			opcao = scanner.nextInt();

		switch (opcao) {

		case 1: 
			System.out.println("Cadastrar Cliente ");
			scanner.nextLine();

			System.out.print("Nome: ");
			String nome = scanner.nextLine();

            System.out.print("Telefone: ");
			
			String telefone = scanner.nextLine();

			System.out.print("Idade: ");
			int idade = scanner.nextInt();
			scanner.nextLine();

			System.out.print("Instagram: ");
			String instagram = scanner.nextLine();

			Cliente cliente = new Cliente(
    			nome,
    			telefone,
    			idade,
    			instagram
			);

		service.cadastrarCliente(cliente);

		System.out.println("Cliente cadastrado com sucesso!");

			break;

        case 2:
    		service.listarClientes();
    		break;

    	case 3:
    		System.out.println("Buscar cliente");
    		System.out.print("Id: ");
			int id = scanner.nextInt();

			Cliente clienteEncontrado = service.buscarClientePorId(id);

			if (clienteEncontrado != null){
				System.out.println("ID: " + clienteEncontrado.getId());
				System.out.println("Nome: " + clienteEncontrado.getNome());
				System.out.println("Telefone: " + clienteEncontrado.getTelefone());
				System.out.println("Idade: " + clienteEncontrado.getIdade());
				System.out.println("Instagram: " + clienteEncontrado.getInstagram());

			} else {
				System.out.println("Cliente nao encontrado. ");
			}



			break;

        case 0:
        	System.out.println("Saindo...");
        	break;

		}

		} while (opcao != 0);

		scanner.close();

	}

}
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
			System.out.println("4 - Remover cliente");
			System.out.println("5 - Atualizar cliente");
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
    		System.out.print("Digite o ID: ");
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

			case 4:

   				System.out.println("Remover cliente");
    			System.out.print("Digite o ID do cliente: ");

    		int idRemover = scanner.nextInt();
			boolean removido = service.removerCliente(idRemover);

			if (removido) {
        		System.out.println("Cliente removido com sucesso!");

	    	} else {
	       		System.out.println("Cliente não encontrado.");
   			}


   			case 5:
    	System.out.println("Atualizar cliente");
		System.out.print("Digite o ID do cliente: ");

    	int idAtualizar = scanner.nextInt();
    		scanner.nextLine();

    		Cliente clienteAtualizar = service.buscarClientePorId(idAtualizar);

    	if (clienteAtualizar != null) {

        System.out.print("Novo nome: ");
        String novoNome = scanner.nextLine();

        System.out.print("Novo telefone: ");
        String novoTelefone = scanner.nextLine();

        System.out.print("Nova idade: ");
        int novaIdade = scanner.nextInt();
        scanner.nextLine();

        System.out.print("Novo Instagram: ");
        String novoInstagram = scanner.nextLine();

        boolean atualizado = service.atualizarCliente(
                idAtualizar,
                novoNome,
                novoTelefone,
                novaIdade,
                novoInstagram
        );

        if (atualizado) {
            System.out.println("Cliente atualizado com sucesso!");
        }

    } else {
        System.out.println("Cliente não encontrado.");
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
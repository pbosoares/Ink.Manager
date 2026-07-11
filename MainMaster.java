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
			System.out.println("0 - Sair");
			System.out.println("Escolha uma opção: ");

			opcao = scanner.nextInt();

		} while (opcao != 0);

		scanner.close();

	}

		}
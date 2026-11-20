import java.util.List;

public class MarcacaoService {

    private MarcacaoRepository repository;

    public MarcacaoService() {
        repository = new MarcacaoRepository();
    }

    public void cadastrarMarcacao(Marcacao marcacao) {
        repository.salvar(marcacao);
    }

    public void listarMarcacoes() {

        List<Marcacao> marcacoes = repository.listarTodas();

        for (Marcacao marcacao : marcacoes) {

            System.out.println("-----------------------");
            System.out.println("ID: " + marcacao.getId());
            System.out.println("ID do Cliente: " + marcacao.getClienteId());
            System.out.println("Data: " + marcacao.getData());
            System.out.println("Horário: " + marcacao.getHorario());
            System.out.println("Descrição: " + marcacao.getDescricao());
            System.out.println("Status: " + marcacao.getStatus());
        }
    }
}
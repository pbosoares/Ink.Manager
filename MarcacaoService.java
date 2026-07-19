import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.BufferedWriter;
import java.io.FileWriter;

public class MarcacaoService {

    private ArrayList<Marcacao> marcacoes;
    private int proximoId = 1;

    private final String ARQUIVO = "marcacoes.txt";

    public MarcacaoService() {
        marcacoes = new ArrayList<>();
        carregarMarcacoes();
    }

    public void cadastrarMarcacao(Marcacao marcacao) {

        marcacao.setId(proximoId);
        proximoId++;

        marcacoes.add(marcacao);

        salvarMarcacoes();
    }

    private void salvarMarcacoes() {

        try (BufferedWriter writer =
                new BufferedWriter(new FileWriter(ARQUIVO))) {

            for (Marcacao marcacao : marcacoes) {

                writer.write(
                    marcacao.getId() + ";" +
                    marcacao.getClienteId() + ";" +
                    marcacao.getData() + ";" +
                    marcacao.getHorario() + ";" +
                    marcacao.getDescricao() + ";" +
                    marcacao.getStatus()
                );

                writer.newLine();
            }

        } catch (IOException e) {

            System.out.println("Erro ao salvar as marcações.");
        }
    }

    private void carregarMarcacoes() {

        try (BufferedReader reader =
                new BufferedReader(new FileReader(ARQUIVO))) {

            String linha;

            while ((linha = reader.readLine()) != null) {

                String[] dados = linha.split(";");

                Marcacao marcacao = new Marcacao();

                marcacao.setId(Integer.parseInt(dados[0]));
                marcacao.setClienteId(Integer.parseInt(dados[1]));
                marcacao.setData(dados[2]);
                marcacao.setHorario(dados[3]);
                marcacao.setDescricao(dados[4]);
                marcacao.setStatus(dados[5]);

                marcacoes.add(marcacao);

                if (marcacao.getId() >= proximoId) {
                    proximoId = marcacao.getId() + 1;
                }
            }

        } catch (IOException e) {

            System.out.println(
                "Nenhum arquivo de marcações encontrado. "
                + "Iniciando sistema vazio."
            );
        }
    }
}
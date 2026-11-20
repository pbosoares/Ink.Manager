import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MarcacaoRepository {

    public void salvar(Marcacao marcacao) {

        String sql = """
                INSERT INTO marcacoes
                (cliente_id, data, horario, descricao, status)
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection conexao = ConnectionFactory.conectar();
             PreparedStatement comando = conexao.prepareStatement(sql)) {

            comando.setInt(1, marcacao.getClienteId());
            comando.setObject(2, marcacao.getData());
            comando.setObject(3, marcacao.getHorario());
            comando.setString(4, marcacao.getDescricao());
            comando.setString(5, marcacao.getStatus().name());

            comando.executeUpdate();

            System.out.println("Marcação salva no banco de dados!");

        } catch (SQLException e) {
            System.out.println("Erro ao salvar marcação:");
            e.printStackTrace();
        }
    }

    public List<Marcacao> listarTodas() {

        List<Marcacao> marcacoes = new ArrayList<>();

        String sql = "SELECT * FROM marcacoes ORDER BY data, horario";

        try (Connection conexao = ConnectionFactory.conectar();
             PreparedStatement comando = conexao.prepareStatement(sql);
             ResultSet resultado = comando.executeQuery()) {

            while (resultado.next()) {

                Marcacao marcacao = new Marcacao();

                marcacao.setId(resultado.getInt("id"));
                marcacao.setClienteId(resultado.getInt("cliente_id"));

                marcacao.setData(
                    resultado.getObject("data", java.time.LocalDate.class)
                );

                marcacao.setHorario(
                    resultado.getObject("horario", java.time.LocalTime.class)
                );

                marcacao.setDescricao(resultado.getString("descricao"));

                marcacao.setStatus(
                    StatusMarcacao.valueOf(resultado.getString("status"))
                );

                marcacoes.add(marcacao);
            }

        } catch (SQLException e) {
            System.out.println("Erro ao listar marcações:");
            e.printStackTrace();
        }

        return marcacoes;
    }
}
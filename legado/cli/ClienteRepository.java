import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ClienteRepository {

    public void salvar(Cliente cliente) {

        String sql = """
                INSERT INTO clientes (nome, telefone, idade, instagram)
                VALUES (?, ?, ?, ?)
                """;

        try (Connection conexao = ConnectionFactory.conectar();
             PreparedStatement comando = conexao.prepareStatement(sql)) {

            comando.setString(1, cliente.getNome());
            comando.setString(2, cliente.getTelefone());
            comando.setInt(3, cliente.getIdade());
            comando.setString(4, cliente.getInstagram());

            comando.executeUpdate();

            System.out.println("Cliente salvo no banco de dados!");

        } catch (SQLException e) {
            System.out.println("Erro ao salvar cliente:");
            e.printStackTrace();
        }
    }

    public List<Cliente> listarTodos() {

        List<Cliente> clientes = new ArrayList<>();

        String sql = "SELECT * FROM clientes";

        try (Connection conexao = ConnectionFactory.conectar();
             PreparedStatement comando = conexao.prepareStatement(sql);
             ResultSet resultado = comando.executeQuery()) {

            while (resultado.next()) {

                Cliente cliente = new Cliente();

                cliente.setId(resultado.getInt("id"));
                cliente.setNome(resultado.getString("nome"));
                cliente.setTelefone(resultado.getString("telefone"));
                cliente.setIdade(resultado.getInt("idade"));
                cliente.setInstagram(resultado.getString("instagram"));

                clientes.add(cliente);
            }

        } catch (SQLException e) {
            System.out.println("Erro ao listar clientes:");
            e.printStackTrace();
        }

        return clientes;
    }
    public Cliente buscarPorId(int id) {

        String sql = "SELECT * FROM clientes WHERE id = ?";

        try (Connection conexao = ConnectionFactory.conectar();
             PreparedStatement comando = conexao.prepareStatement(sql)) {

            comando.setInt(1, id);

            try (ResultSet resultado = comando.executeQuery()) {

                if (resultado.next()) {

                    Cliente cliente = new Cliente();

                    cliente.setId(resultado.getInt("id"));
                    cliente.setNome(resultado.getString("nome"));
                    cliente.setTelefone(resultado.getString("telefone"));
                    cliente.setIdade(resultado.getInt("idade"));
                    cliente.setInstagram(resultado.getString("instagram"));

                    return cliente;
                }
            }

        } catch (SQLException e) {
            System.out.println("Erro ao buscar cliente:");
            e.printStackTrace();
        }

        return null;
    }
    public boolean removerPorId(int id) {

    String sql = "DELETE FROM clientes WHERE id = ?";

    try (Connection conexao = ConnectionFactory.conectar();
         PreparedStatement comando = conexao.prepareStatement(sql)) {

        comando.setInt(1, id);

        int linhasAfetadas = comando.executeUpdate();

        return linhasAfetadas > 0;

    } catch (SQLException e) {
        System.out.println("Erro ao remover cliente:");
        e.printStackTrace();
    }

    return false;
}

public boolean atualizar(Cliente cliente) {

    String sql = """
            UPDATE clientes
            SET nome = ?, telefone = ?, idade = ?, instagram = ?
            WHERE id = ?
            """;

    try (Connection conexao = ConnectionFactory.conectar();
         PreparedStatement comando = conexao.prepareStatement(sql)) {

        comando.setString(1, cliente.getNome());
        comando.setString(2, cliente.getTelefone());
        comando.setInt(3, cliente.getIdade());
        comando.setString(4, cliente.getInstagram());
        comando.setInt(5, cliente.getId());

        int linhasAfetadas = comando.executeUpdate();

        return linhasAfetadas > 0;

    } catch (SQLException e) {
        System.out.println("Erro ao atualizar cliente:");
        e.printStackTrace();
    }

    return false;
}

    }

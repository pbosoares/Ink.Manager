import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnectionFactory {

    private static final String URL =
            "jdbc:postgresql://localhost:5432/ink_manager";

    private static final String USUARIO = "postgres";

    private static final String SENHA = System.getenv("DB_PASSWORD");

    public static Connection conectar() throws SQLException {

        if (SENHA == null || SENHA.isBlank()) {
            throw new SQLException(
                "Variável de ambiente DB_PASSWORD não configurada."
            );
        }

        return DriverManager.getConnection(URL, USUARIO, SENHA);
    }
}
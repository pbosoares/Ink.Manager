package ink.manager.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:studio;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "jwt.secret=test-only-secret-with-more-than-32-bytes"
})
class StudioIntegrationTest {
    @Value("${local.server.port}")
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private HttpResponse<String> request(String method, String path, String body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(15));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(HttpResponse<String> response) {
        return mapper.readTree(response.body());
    }

    @Test
    void interfacePublicaEDadosProtegidos() throws Exception {
        var home = request("GET", "/", null, null);
        assertEquals(200, home.statusCode());
        assertTrue(home.body().contains("Ink.Manager"));
        for (String path : new String[] { "/app.js", "/styles.css", "/favicon.svg" }) {
            assertEquals(200, request("GET", path, null, null).statusCode(), path);
        }
        assertEquals(401, request("GET", "/clientes", null, null).statusCode());
        assertEquals(401, request("GET", "/marcacoes", null, "invalid").statusCode());
    }

    @Test
    void fluxoCompletoDoEstudio() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String account = """
                {"id":999,"nome":"Artista","email":"%s","senha":"SenhaTeste123"}
                """.formatted(email);
        var created = request("POST", "/auth/cadastro", account, null);
        assertEquals(200, created.statusCode(), created.body());
        assertFalse(json(created).has("senha"));
        assertNotEquals(999L, json(created).get("id").asLong());
        assertEquals(409, request("POST", "/auth/cadastro", account, null).statusCode());
        assertEquals(400, request("POST", "/auth/cadastro", "{}", null).statusCode());
        assertEquals(401, request("POST", "/auth/login", "{\"email\":\"" + email + "\",\"senha\":\"errada\"}", null).statusCode());
        var login = request("POST", "/auth/login", "{\"email\":\"" + email + "\",\"senha\":\"SenhaTeste123\"}", null);
        assertEquals(200, login.statusCode(), login.body());
        String token = json(login).get("token").asText();

        assertEquals(400, request("POST", "/clientes", "{}", token).statusCode());
        var customer = request("POST", "/clientes", "{\"id\":999,\"nome\":\"Maria\",\"telefone\":\"21999999999\",\"idade\":30}", token);
        assertEquals(201, customer.statusCode(), customer.body());
        long customerId = json(customer).get("id").asLong();
        assertNotEquals(999L, customerId);
        assertEquals(200, request("GET", "/clientes", null, token).statusCode());

        assertEquals(400, request("POST", "/marcacoes", "{}", token).statusCode());
        String appointment = """
                {"id":999,"cliente":{"id":%d},"data":"2026-10-15","horario":"14:30","descricao":"Rosa tradicional","status":"AGENDADA"}
                """.formatted(customerId);
        assertEquals(400, request("POST", "/marcacoes", appointment.replace("{\"id\":" + customerId + "}", "{}"), token).statusCode());
        assertEquals(404, request("POST", "/marcacoes", appointment.replace("{\"id\":" + customerId + "}", "{\"id\":999999}"), token).statusCode());
        assertEquals(400, request("POST", "/marcacoes", appointment.replace("AGENDADA", "INVALIDO"), token).statusCode());
        var session = request("POST", "/marcacoes", appointment, token);
        assertEquals(201, session.statusCode(), session.body());
        long appointmentId = json(session).get("id").asLong();
        assertNotEquals(999L, appointmentId);
        assertEquals(409, request("DELETE", "/clientes/" + customerId, null, token).statusCode());
        assertEquals(200, request("GET", "/marcacoes", null, token).statusCode());
        var updated = request("PUT", "/marcacoes/" + appointmentId, appointment.replace("AGENDADA", "CONFIRMADA"), token);
        assertEquals(200, updated.statusCode(), updated.body());
        assertEquals("CONFIRMADA", json(updated).get("status").asText());
        assertEquals(400, request("PUT", "/marcacoes/" + appointmentId, "{}", token).statusCode());
        var updatedCustomer = request("PUT", "/clientes/" + customerId, "{\"nome\":\"Maria Silva\",\"telefone\":\"21988888888\"}", token);
        assertEquals(200, updatedCustomer.statusCode());
        assertEquals("Maria Silva", json(request("GET", "/marcacoes/" + appointmentId, null, token)).get("cliente").get("nome").asText());
        assertEquals(204, request("DELETE", "/marcacoes/" + appointmentId, null, token).statusCode());
        assertEquals(404, request("DELETE", "/marcacoes/" + appointmentId, null, token).statusCode());
        assertEquals(204, request("DELETE", "/clientes/" + customerId, null, token).statusCode());
        assertEquals(404, request("DELETE", "/clientes/" + customerId, null, token).statusCode());
    }
}

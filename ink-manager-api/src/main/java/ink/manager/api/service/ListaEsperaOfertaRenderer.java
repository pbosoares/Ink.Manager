package ink.manager.api.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.text.NumberFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ListaEsperaOfertaRenderer implements EmailOutboxRenderer {
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private final ObjectMapper json;
    private final ListaEsperaService lista;
    private final Clock clock;
    private final String publicUrl;
    private final String jwtSecret;

    public ListaEsperaOfertaRenderer(ObjectMapper json, ListaEsperaService lista, Clock clock,
            @Value("${app.public-url:http://localhost:8080}") String publicUrl,
            @Value("${jwt.secret}") String jwtSecret) {
        this.json = json;
        this.lista = lista;
        this.clock = clock;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
        this.jwtSecret = jwtSecret;
    }

    @Override
    public boolean suporta(String tipo) {
        return "LISTA_ESPERA_OFERTA".equals(tipo);
    }

    @Override
    public EmailOutboxConteudo render(EmailOutboxJob job) {
        final JsonNode payload;
        try {
            payload = json.readTree(job.dadosJson());
        } catch (Exception invalidJson) {
            throw new IllegalArgumentException("Invalid waiting-list email payload");
        }
        long ofertaId = payload == null ? 0 : payload.path("ofertaId").asLong(0);
        if (ofertaId <= 0) throw new IllegalArgumentException("Missing waiting-list offer reference");

        String token = tokenDeterministico(job, ofertaId);
        var oferta = lista.prepararEmailOferta(job.estudioId(), ofertaId, token, clock.instant()).orElse(null);
        if (oferta == null) return null;

        String link = publicUrl + "/a/" + PublicLinkCode.encode(oferta.linkPublico()) + "#token=" + token;
        String prazo = oferta.expiraEm().atZone(java.time.ZoneId.of(oferta.fusoHorario()))
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'as' HH:mm"));
        String texto = "Uma vaga compativel com seu pedido esta disponivel.\n\n"
            + "Servico: " + oferta.servico() + "\n"
            + "Profissional: " + oferta.profissional() + "\n"
            + "Data: " + oferta.data().format(DATA) + "\n"
            + "Horario: " + oferta.horario().format(HORA) + " (" + oferta.fusoHorario() + ")\n"
            + "Duracao: " + oferta.duracaoMinutos() + " minutos\n"
            + "Valor: " + NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(oferta.preco()) + "\n\n"
            + "A oferta pode ser aceita ate " + prazo + ".\n"
            + "Consulte os detalhes e aceite a vaga: " + link;
        return new EmailOutboxConteudo("Surgiu uma vaga para voce", texto);
    }

    @Override
    public void falhouDefinitivamente(EmailOutboxJob job) {
        lista.falhaFinalOferta(job.estudioId(), job.referencia());
    }

    private String tokenDeterministico(EmailOutboxJob job, long ofertaId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] token = mac.doFinal(("marcae:lista-espera:v1:" + job.estudioId() + ":"
                + job.chaveIdempotencia() + ":" + ofertaId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(token);
        } catch (Exception failure) {
            throw new IllegalStateException("Could not derive waiting-list offer token");
        }
    }
}

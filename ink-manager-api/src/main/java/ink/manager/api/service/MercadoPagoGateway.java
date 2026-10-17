package ink.manager.api.service;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.*;
import ink.manager.api.exception.ApiException;

@Service
public class MercadoPagoGateway {
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper json=new ObjectMapper();
    private final String token;
    private final String webhookSecret;
    private final String publicUrl;
    public MercadoPagoGateway(@Value("${billing.mp-access-token:}") String token,
            @Value("${billing.mp-webhook-secret:}") String webhookSecret,@Value("${app.public-url}") String publicUrl){
        this.token=token;this.webhookSecret=webhookSecret;this.publicUrl=publicUrl;
    }
    public boolean disponivel(){return !token.isBlank()&&!webhookSecret.isBlank()&&publicUrl.startsWith("https://");}
    public JsonNode criar(String path,Map<String,Object> body,String idempotencia){return chamar("POST",path,body,idempotencia);}
    public JsonNode obter(String path){return chamar("GET",path,null,null);}
    public JsonNode atualizar(String path,Map<String,Object> body){return chamar("PUT",path,body,null);}
    private JsonNode chamar(String method,String path,Map<String,Object> body,String idempotencia){
        if(!disponivel()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Cobranca ainda nao configurada na plataforma.");
        if(!path.startsWith("/")||path.contains("..")||path.contains("://")) throw new IllegalArgumentException("Caminho invalido.");
        var request=HttpRequest.newBuilder(URI.create("https://api.mercadopago.com"+path)).timeout(Duration.ofSeconds(5))
            .header("Authorization","Bearer "+token).header("Content-Type","application/json");
        if(idempotencia!=null)request.header("X-Idempotency-Key",idempotencia);
        try{
            request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            var response=client.send(request.build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()<200||response.statusCode()>=300) throw new ApiException(HttpStatus.BAD_GATEWAY,"Provedor de pagamento indisponivel. Tente novamente.");
            return json.readTree(response.body());
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Consulta de pagamento interrompida.");}
        catch(java.io.IOException error){throw new ApiException(HttpStatus.BAD_GATEWAY,"Nao foi possivel conectar ao provedor de pagamento.");}
    }
}

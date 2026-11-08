package ink.manager.api.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import ink.manager.api.exception.ApiException;

@Service
public class StripeGateway {
    // Keep the request contract stable: newer major releases remove checkout parameters.
    private static final String API_VERSION = "2025-03-31.basil";
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper json=new ObjectMapper();
    private final String secretKey,webhookSecret,publicUrl;

    public StripeGateway(@Value("${billing.stripe-secret-key:}") String secretKey,
            @Value("${billing.stripe-webhook-secret:}") String webhookSecret,
            @Value("${app.public-url}") String publicUrl){
        this.secretKey=secretKey;this.webhookSecret=webhookSecret;this.publicUrl=publicUrl.replaceAll("/+$","");
    }
    public boolean disponivel(){return !secretKey.isBlank()&&!webhookSecret.isBlank()&&publicUrl.startsWith("https://");}
    public JsonNode criarCheckout(String checkoutId,long estudioId,String metodo,String email,int trialDays,int valorCentavos){
        var form=new java.util.LinkedHashMap<String,String>();
        form.put("mode",metodo.equals("CARTAO")?"subscription":"payment");
        form.put("payment_method_types[0]",metodo.equals("CARTAO")?"card":"pix");
        form.put("customer_email",email);form.put("client_reference_id",checkoutId);
        form.put("metadata[checkout_id]",checkoutId);form.put("metadata[studio_id]",Long.toString(estudioId));
        form.put("line_items[0][quantity]","1");form.put("line_items[0][price_data][currency]","brl");
        form.put("line_items[0][price_data][unit_amount]",Integer.toString(valorCentavos));
        form.put("line_items[0][price_data][product_data][name]","Marcaê - mensalidade");
        if(metodo.equals("CARTAO")){
            form.put("line_items[0][price_data][recurring][interval]","month");
            // This flow is entered only after the owner chooses card billing; collect
            // the method now so the subscription can renew when its trial ends.
            form.put("payment_method_collection","always");
            if(trialDays>0){
                form.put("subscription_data[trial_period_days]",Integer.toString(trialDays));
                form.put("subscription_data[trial_settings][end_behavior][missing_payment_method]","cancel");
            }
            form.put("subscription_data[metadata][checkout_id]",checkoutId);
            form.put("subscription_data[metadata][studio_id]",Long.toString(estudioId));
        }else{
            form.put("payment_intent_data[metadata][checkout_id]",checkoutId);
            form.put("payment_intent_data[metadata][studio_id]",Long.toString(estudioId));
        }
        form.put("success_url",publicUrl+"/?assinatura=retorno&session_id={CHECKOUT_SESSION_ID}");
        form.put("cancel_url",publicUrl+"/?assinatura=cancelada");
        return chamar("POST","/v1/checkout/sessions",form,checkoutId);
    }
    public JsonNode checkout(String sessionId){return chamar("GET","/v1/checkout/sessions/"+id(sessionId),Map.of(),null);}
    public JsonNode subscription(String subscriptionId){return chamar("GET","/v1/subscriptions/"+id(subscriptionId),Map.of(),null);}
    public JsonNode paymentIntent(String paymentIntentId){return chamar("GET","/v1/payment_intents/"+id(paymentIntentId),Map.of(),null);}
    public JsonNode invoice(String invoiceId){return chamar("GET","/v1/invoices/"+id(invoiceId),Map.of(),null);}
    public JsonNode invoicesPagas(String subscriptionId,int limit,String startingAfter){
        var query=new java.util.LinkedHashMap<String,String>();query.put("subscription",id(subscriptionId));query.put("status","paid");query.put("limit",Integer.toString(validarLimit(limit)));
        adicionarCursor(query,startingAfter);return chamar("GET","/v1/invoices",query,null);
    }
    public JsonNode invoicePaymentsPorPaymentIntent(String paymentIntentId,int limit,String startingAfter){
        var query=new java.util.LinkedHashMap<String,String>();query.put("payment[type]","payment_intent");query.put("payment[payment_intent]",id(paymentIntentId));query.put("limit",Integer.toString(validarLimit(limit)));
        adicionarCursor(query,startingAfter);return chamar("GET","/v1/invoice_payments",query,null);
    }
    public JsonNode invoicePaymentsPorInvoice(String invoiceId,int limit,String startingAfter){
        var query=new java.util.LinkedHashMap<String,String>();query.put("invoice",id(invoiceId));query.put("limit",Integer.toString(validarLimit(limit)));
        adicionarCursor(query,startingAfter);return chamar("GET","/v1/invoice_payments",query,null);
    }
    public JsonNode reembolsosDesde(long createdGte,int limit,String cursor){
        if(createdGte<0)throw new ApiException(HttpStatus.BAD_REQUEST,"Data inicial Stripe invalida.");
        var query=new java.util.LinkedHashMap<String,String>();query.put("type","charge.refunded");query.put("created[gte]",Long.toString(createdGte));query.put("limit",Integer.toString(validarLimit(limit)));
        adicionarCursor(query,cursor);return chamar("GET","/v1/events",query,null);
    }
    public JsonNode agendarCancelamentoSubscription(String subscriptionId,long cancellationCycle){String valid=id(subscriptionId);return chamar("POST","/v1/subscriptions/"+valid,Map.of("cancel_at_period_end","true"),"cancel-at-period-end:"+valid+":"+cancellationCycle);}
    public JsonNode reativarSubscription(String subscriptionId,long cancellationCycle){String valid=id(subscriptionId);return chamar("POST","/v1/subscriptions/"+valid,Map.of("cancel_at_period_end","false"),"reactivate-cancel-at-period-end:"+valid+":"+cancellationCycle);}
    private int validarLimit(int limit){if(limit<1||limit>100)throw new ApiException(HttpStatus.BAD_REQUEST,"Limite de pagina Stripe deve estar entre 1 e 100.");return limit;}
    private void adicionarCursor(Map<String,String> query,String startingAfter){if(startingAfter!=null&&!startingAfter.isBlank())query.put("starting_after",id(startingAfter));}
    private String id(String value){if(value==null||!value.matches("[A-Za-z0-9_]{1,100}"))throw new ApiException(HttpStatus.BAD_REQUEST,"Identificador Stripe invalido.");return value;}
    private JsonNode chamar(String method,String path,Map<String,String> values,String idem){
        if(!disponivel())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Stripe ainda nao esta configurado na plataforma.");
        String query=method.equals("GET")?values.entrySet().stream().map(e->enc(e.getKey())+"="+enc(e.getValue())).collect(Collectors.joining("&")):"";
        String url="https://api.stripe.com"+path+(query.isEmpty()?"":"?"+query);
        var request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
            .header("Authorization","Bearer "+secretKey).header("Content-Type","application/x-www-form-urlencoded")
            .header("Stripe-Version",API_VERSION);
        if(idem!=null)request.header("Idempotency-Key",idem);
        String form=method.equals("GET")?"":values.entrySet().stream().map(e->enc(e.getKey())+"="+enc(e.getValue())).collect(Collectors.joining("&"));
        try{
            request.method(method,method.equals("GET")?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(form));
            var response=client.send(request.build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()<200||response.statusCode()>=300)throw new ApiException(HttpStatus.BAD_GATEWAY,"Stripe recusou a operacao. Confira a configuracao e tente novamente.");
            return json.readTree(response.body());
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Consulta Stripe interrompida.");}
        catch(java.io.IOException error){throw new ApiException(HttpStatus.BAD_GATEWAY,"Nao foi possivel conectar ao Stripe.");}
    }
    private String enc(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
}

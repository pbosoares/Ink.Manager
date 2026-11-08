package ink.manager.api.controller;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ink.manager.api.config.MercadoPagoSignature;
import ink.manager.api.config.StripeWebhookSignature;
import ink.manager.api.service.AssinaturaService;
import tools.jackson.databind.JsonNode;
@RestController @RequestMapping("/assinatura")
public class AssinaturaController {
    public record CheckoutRequest(@NotNull AssinaturaService.Metodo metodo){}
    private final AssinaturaService service;
    private final MercadoPagoSignature assinatura;
    private final StripeWebhookSignature stripeAssinatura;
    public AssinaturaController(AssinaturaService service,MercadoPagoSignature assinatura,StripeWebhookSignature stripeAssinatura){this.service=service;this.assinatura=assinatura;this.stripeAssinatura=stripeAssinatura;}
    @GetMapping public AssinaturaService.Resumo consultar(){return service.resumo();}
    @PostMapping("/checkout") @PreAuthorize("hasRole('PROPRIETARIO')")
    public AssinaturaService.LinkCheckout checkout(@Valid @RequestBody CheckoutRequest request){return service.checkout(request.metodo());}
    @PostMapping("/sincronizar") @PreAuthorize("hasRole('PROPRIETARIO')")
    public AssinaturaService.Resumo sincronizar(){return service.sincronizar();}
    @PostMapping("/cancelar-recorrencia") @PreAuthorize("hasRole('PROPRIETARIO')")
    public AssinaturaService.Resumo cancelar(){return service.cancelarRecorrencia();}
    @PostMapping("/reativar-recorrencia") @PreAuthorize("hasRole('PROPRIETARIO')")
    public AssinaturaService.Resumo reativar(){return service.reativarRecorrencia();}
    @PostMapping("/webhook")
    public void webhook(@RequestHeader(name="x-signature",required=false) String signature,@RequestHeader(name="x-request-id",required=false) String requestId,
            @RequestParam(name="data.id",required=false) String dataId,@RequestBody JsonNode notification){
        assinatura.validar(signature,requestId,dataId);service.notificar(notification.path("type").asText(),dataId);
    }
    @PostMapping("/stripe-webhook")
    public void stripeWebhook(@RequestHeader(name="Stripe-Signature",required=false) String signature,@RequestBody String rawBody){
        stripeAssinatura.validar(signature,rawBody);
        final JsonNode event;
        try{event=new tools.jackson.databind.ObjectMapper().readTree(rawBody);}
        catch(tools.jackson.core.JacksonException invalid){throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Notificacao Stripe invalida.");}
        service.notificarStripe(event);
    }
}

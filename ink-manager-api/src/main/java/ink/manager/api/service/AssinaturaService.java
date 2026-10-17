package ink.manager.api.service;

import java.math.BigDecimal;
import java.net.URI;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import ink.manager.api.config.AcessoAtual;
import ink.manager.api.exception.ApiException;
import ink.manager.api.repository.EstudioRepository;

@Service
public class AssinaturaService {
    public enum Metodo { CARTAO, PIX }
    public record Resumo(String status,BigDecimal mensalidade,String moeda,LocalDateTime avaliacaoAte,
            LocalDateTime pagoAte,boolean cobrancaDisponivel,boolean cobrancaObrigatoria,boolean cartaoEmRecorrencia){}
    public record LinkCheckout(String id,String url){}
    private final AssinaturaStore store;
    private final EstudioRepository estudios;
    private final UsuarioService usuarios;
    private final MercadoPagoGateway gateway;
    private final AcessoAtual acesso;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final BigDecimal valor;
    private final int trialDays;
    private final boolean enforce;
    private final String publicUrl;

    public AssinaturaService(AssinaturaStore store,EstudioRepository estudios,UsuarioService usuarios,
            MercadoPagoGateway gateway,AcessoAtual acesso,Clock clock,PlatformTransactionManager manager,
            @Value("${billing.monthly-amount:19.90}") BigDecimal valor,@Value("${billing.trial-days:14}") int trialDays,
            @Value("${billing.enforce:true}") boolean enforce,@Value("${app.public-url}") String publicUrl){
        this.store=store;this.estudios=estudios;this.usuarios=usuarios;this.gateway=gateway;this.acesso=acesso;
        this.clock=clock;this.tx=new TransactionTemplate(manager);this.valor=valor.setScale(2);this.trialDays=trialDays;
        this.enforce=enforce;this.publicUrl=publicUrl.replaceAll("/+$","");
        if(valor.signum()<=0||trialDays<0||trialDays>30)throw new IllegalArgumentException("Configuracao de assinatura invalida.");
    }
    public Resumo resumo(){return resumo(acesso.estudioId());}
    public Resumo resumo(long id){
        var estudio=estudios.findById(id).orElseThrow();var agora=LocalDateTime.now(clock);
        var trial=estudio.getCriadoEm().plusDays(trialDays);var pago=estudio.getPagoAte();
        String status=pago!=null&&pago.isAfter(agora)?"ATIVA":trial.isAfter(agora)?"AVALIACAO":"EXPIRADA";
        boolean recorrente=store.aberto(id,"CARTAO").map(c->"authorized".equals(c.status())).orElse(false);
        return new Resumo(status,valor,"BRL",trial,pago,gateway.disponivel(),enforce,recorrente);
    }
    public boolean podeGravar(long id){return !enforce||!"EXPIRADA".equals(resumo(id).status());}
    public LinkCheckout checkout(Metodo metodo){
        if(!gateway.disponivel())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Pagamento ainda nao configurado na plataforma.");
        long estudioId=acesso.estudioId();String email=usuarios.atual().getEmail();
        // Persist the intent before the external request. Retries reuse its idempotency key.
        String id=tx.execute(status->{
            estudios.bloquear(estudioId).orElseThrow();var aberto=store.aberto(estudioId,metodo.name());
            if(aberto.isPresent()&&(metodo==Metodo.CARTAO||aberto.get().criadoEm().plusHours(24).isAfter(LocalDateTime.now(clock))))return aberto.get().id();
            String novo=UUID.randomUUID().toString();store.criar(novo,estudioId,metodo.name(),valor,LocalDateTime.now(clock));return novo;
        });
        return tx.execute(status->{
            estudios.bloquear(estudioId).orElseThrow();var atual=store.checkout(id).orElseThrow();
            if(atual.url()!=null)return new LinkCheckout(id,atual.url());
            var body=new HashMap<String,Object>();body.put("external_reference",id);
            body.put("notification_url",publicUrl+"/assinatura/webhook");
            JsonNode response;
            if(metodo==Metodo.CARTAO){
                body.put("reason","Ink Manager - assinatura mensal");body.put("payer_email",email);body.put("status","pending");
                body.put("back_url",publicUrl+"/?assinatura=retorno");
                body.put("auto_recurring",Map.of("frequency",1,"frequency_type","months","transaction_amount",atual.valor(),"currency_id","BRL"));
                response=gateway.criar("/preapproval",body,id);
            }else{
                body.put("items",List.of(Map.of("id","ink-manager-mensal","title","Ink Manager - mensalidade por Pix","quantity",1,"unit_price",atual.valor(),"currency_id","BRL")));
                body.put("payer",Map.of("email",email));body.put("back_urls",Map.of("success",publicUrl+"/?assinatura=retorno","pending",publicUrl+"/?assinatura=retorno","failure",publicUrl+"/?assinatura=retorno"));
                body.put("auto_return","approved");body.put("expires",true);
                body.put("expiration_date_from",clock.instant().toString());body.put("expiration_date_to",clock.instant().plus(Duration.ofHours(24)).toString());
                body.put("payment_methods",Map.of("default_payment_method_id","pix","excluded_payment_types",List.of(Map.of("id","credit_card"),Map.of("id","debit_card"),Map.of("id","prepaid_card"),Map.of("id","ticket")),"installments",1));
                response=gateway.criar("/checkout/preferences",body,id);
            }
            String providerId=identificador(response.path("id").asText());String url=response.path("init_point").asText();validarUrl(url);
            store.registrar(id,estudioId,providerId,url);return new LinkCheckout(id,url);
        });
    }
    private void validarUrl(String url){
        try{var uri=URI.create(url);String host=uri.getHost();
            if(!"https".equals(uri.getScheme())||host==null||!(host.equals("www.mercadopago.com.br")||host.endsWith(".mercadopago.com.br")||host.equals("www.mercadopago.com")||host.endsWith(".mercadopago.com")))throw new IllegalArgumentException();
        }catch(RuntimeException invalid){throw new ApiException(HttpStatus.BAD_GATEWAY,"Checkout retornou um endereco invalido.");}
    }
    public void notificar(String tipo,String id){
        identificador(id);
        switch(tipo){
            case "payment" -> processarPagamento(gateway.obter("/v1/payments/"+id),null);
            case "subscription_authorized_payment" -> processarFatura(gateway.obter("/authorized_payments/"+id));
            case "subscription_preapproval" -> processarContrato(gateway.obter("/preapproval/"+id));
            default -> { }
        }
    }
    private void processarContrato(JsonNode contrato){
        var checkout=store.checkout(contrato.path("external_reference").asText()).orElse(null);
        if(checkout==null||!"CARTAO".equals(checkout.metodo())||!Objects.equals(checkout.providerId(),contrato.path("id").asText()))return;
        tx.executeWithoutResult(status->{estudios.bloquear(checkout.estudioId()).orElseThrow();store.statusContrato(checkout.id(),checkout.estudioId(),contrato.path("status").asText(),providerUpdatedAt(contrato,"last_modified"));});
    }
    private void processarFatura(JsonNode fatura){
        String contratoId=identificador(fatura.path("preapproval_id").asText());var contrato=gateway.obter("/preapproval/"+contratoId);
        var checkout=store.checkout(contrato.path("external_reference").asText()).orElse(null);
        if(checkout==null||!"CARTAO".equals(checkout.metodo())||!Objects.equals(checkout.providerId(),contratoId))return;
        processarContrato(contrato);
        String paymentId=fatura.path("payment").path("id").asText();
        if(!paymentId.isBlank())processarPagamento(gateway.obter("/v1/payments/"+identificador(paymentId)),checkout.id());
    }
    private void processarPagamento(JsonNode pagamento,String referenciaConfirmada){
        String referencia=referenciaConfirmada==null?pagamento.path("external_reference").asText():referenciaConfirmada;
        var checkout=store.checkout(referencia).orElse(null);if(checkout==null)return;
        String id=identificador(pagamento.path("id").asText());
        if(!"BRL".equals(pagamento.path("currency_id").asText())||new BigDecimal(pagamento.path("transaction_amount").asText("0")).compareTo(checkout.valor())!=0)
            throw new ApiException(HttpStatus.BAD_REQUEST,"Pagamento nao corresponde a mensalidade.");
        boolean aprovado="approved".equals(pagamento.path("status").asText())&&new BigDecimal(pagamento.path("transaction_amount_refunded").asText("0")).signum()==0;
        LocalDateTime aprovadoEm=aprovado?OffsetDateTime.parse(pagamento.path("date_approved").asText()).atZoneSameInstant(clock.getZone()).toLocalDateTime():null;
        tx.executeWithoutResult(status->{
            var estudio=estudios.bloquear(checkout.estudioId()).orElseThrow();var existente=store.pagamento(id);
            if(existente.isPresent()&&!existente.get().checkoutId().equals(checkout.id()))throw new ApiException(HttpStatus.CONFLICT,"Pagamento ja associado a outra assinatura.");
            long updatedAt=providerUpdatedAt(pagamento,"date_last_updated");
            if(existente.isPresent()&&existente.get().providerUpdatedAt()>updatedAt)return;
            // A refunded approval cannot be restored by a delayed older response.
            if(aprovado&&existente.isPresent()&&!existente.get().aprovado()&&existente.get().aprovadoEm()!=null)return;
            LocalDateTime dataCredito=existente.map(AssinaturaStore.Pagamento::aprovadoEm).orElse(aprovadoEm);
            store.gravarPagamento(new AssinaturaStore.Pagamento(id,checkout.id(),checkout.estudioId(),aprovado,null,dataCredito,updatedAt));
            if("PIX".equals(checkout.metodo()))store.status(checkout.id(),checkout.estudioId(),pagamento.path("status").asText());
        });
    }
    public Resumo sincronizar(){
        for(var checkout:store.recentes(acesso.estudioId()))sincronizarCheckout(checkout);
        return resumo();
    }
    public void sincronizarCheckout(AssinaturaStore.Checkout checkout){
        if(!gateway.disponivel()||checkout.providerId()==null)return;
        if("CARTAO".equals(checkout.metodo())){
            processarContrato(gateway.obter("/preapproval/"+identificador(checkout.providerId())));
            for(var fatura:gateway.obter("/authorized_payments/search?preapproval_id="+identificador(checkout.providerId())+"&limit=20").path("results"))processarFatura(fatura);
        }else{
            for(var pagamento:gateway.obter("/v1/payments/search?external_reference="+checkout.id()+"&sort=date_created&criteria=desc&limit=20").path("results"))
                processarPagamento(gateway.obter("/v1/payments/"+identificador(pagamento.path("id").asText())),checkout.id());
        }
    }
    public Resumo cancelarRecorrencia(){
        var checkout=store.aberto(acesso.estudioId(),"CARTAO").orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Nenhuma recorrencia ativa."));
        var contrato=gateway.atualizar("/preapproval/"+identificador(checkout.providerId()),Map.of("status","cancelled"));
        if(!"cancelled".equals(contrato.path("status").asText()))throw new ApiException(HttpStatus.BAD_GATEWAY,"O provedor ainda nao confirmou o cancelamento.");
        processarContrato(contrato);return resumo();
    }
    private long providerUpdatedAt(JsonNode resource,String field){
        String value=resource.path(field).asText();
        return value.isBlank()?0:OffsetDateTime.parse(value).toInstant().toEpochMilli();
    }
    private String identificador(String id){if(id==null||!id.matches("[A-Za-z0-9_-]{1,100}"))throw new ApiException(HttpStatus.BAD_REQUEST,"Identificador de pagamento invalido.");return id;}
}

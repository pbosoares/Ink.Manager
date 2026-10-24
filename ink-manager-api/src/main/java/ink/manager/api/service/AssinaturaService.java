package ink.manager.api.service;

import java.math.BigDecimal;
import java.net.URI;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log=LoggerFactory.getLogger(AssinaturaService.class);
    public enum Metodo { CARTAO, PIX }
    public record Resumo(String status,BigDecimal mensalidade,String moeda,LocalDateTime avaliacaoAte,
            LocalDateTime pagoAte,boolean cobrancaDisponivel,boolean cobrancaObrigatoria,boolean cartaoEmRecorrencia){}
    public record LinkCheckout(String id,String url){}
    private final AssinaturaStore store;
    private final EstudioRepository estudios;
    private final UsuarioService usuarios;
    private final MercadoPagoGateway gateway;
    private final StripeGateway stripe;
    private final AcessoAtual acesso;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final BigDecimal valor;
    private final int trialDays;
    private final boolean enforce;
    private final String publicUrl;
    private final String provider;

    public AssinaturaService(AssinaturaStore store,EstudioRepository estudios,UsuarioService usuarios,
            MercadoPagoGateway gateway,StripeGateway stripe,AcessoAtual acesso,Clock clock,PlatformTransactionManager manager,
            @Value("${billing.monthly-amount:29.90}") BigDecimal valor,@Value("${billing.trial-days:14}") int trialDays,
            @Value("${billing.enforce:true}") boolean enforce,@Value("${app.public-url}") String publicUrl,
            @Value("${billing.provider:stripe}") String provider){
        this.store=store;this.estudios=estudios;this.usuarios=usuarios;this.gateway=gateway;this.stripe=stripe;this.acesso=acesso;
        this.clock=clock;this.tx=new TransactionTemplate(manager);this.valor=valor.setScale(2);this.trialDays=trialDays;
        this.enforce=enforce;this.publicUrl=publicUrl.replaceAll("/+$","");this.provider=provider.toUpperCase(Locale.ROOT);
        if(!Set.of("STRIPE","MERCADOPAGO").contains(this.provider))throw new IllegalArgumentException("Provedor de cobranca invalido.");
        if(valor.signum()<=0||trialDays<0||trialDays>30)throw new IllegalArgumentException("Configuracao de assinatura invalida.");
    }
    public Resumo resumo(){return resumo(acesso.estudioId());}
    public Resumo resumo(long id){
        var estudio=estudios.findById(id).orElseThrow();var agora=LocalDateTime.now(clock);
        var trial=estudio.getCriadoEm().plusDays(trialDays);var pago=estudio.getPagoAte();
        String status=pago!=null&&pago.isAfter(agora)?"ATIVA":trial.isAfter(agora)?"AVALIACAO":"EXPIRADA";
        boolean recorrente=store.aberto(id,"CARTAO",provider).map(c->provider.equals("STRIPE")?Set.of("trialing","active","past_due").contains(c.status()):"authorized".equals(c.status())).orElse(false);
        return new Resumo(status,valor,"BRL",trial,pago,provedorDisponivel(),enforce,recorrente);
    }
    public boolean podeGravar(long id){return !enforce||!"EXPIRADA".equals(resumo(id).status());}
    public LinkCheckout checkout(Metodo metodo){
        if(!provedorDisponivel())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Stripe ainda nao esta configurado na plataforma.");
        long estudioId=acesso.estudioId();String email=usuarios.atual().getEmail();
        // Persist the intent before the external request. Retries reuse its idempotency key.
        String id=tx.execute(status->{
            estudoAbertoParaCheckout(estudioId);var aberto=store.aberto(estudioId,metodo.name(),provider);
            if(aberto.isPresent()&&aberto.get().valor().compareTo(valor)==0&&
                    (Set.of("authorized","paused","trialing","active","past_due").contains(aberto.get().status())||aberto.get().criadoEm().plusHours(24).isAfter(LocalDateTime.now(clock))))return aberto.get().id();
            String novo=UUID.randomUUID().toString();store.criar(novo,estudioId,metodo.name(),valor,provider,LocalDateTime.now(clock));return novo;
        });
        return tx.execute(status->{
            estudoAbertoParaCheckout(estudioId);var atual=store.checkout(id).orElseThrow();
            if(atual.url()!=null)return new LinkCheckout(id,atual.url());
            if(provider.equals("STRIPE")){
                var estudio=estudios.findById(estudioId).orElseThrow();var deadline=estudio.getCriadoEm().plusDays(trialDays);
                long remainingSeconds=Duration.between(LocalDateTime.now(clock),deadline).getSeconds();
                long remainingDays=remainingSeconds<=0?0:(remainingSeconds+86_399L)/86_400L;
                int diasTrial=(int)Math.min(trialDays,remainingDays);
                var response=stripe.criarCheckout(id,estudioId,metodo.name(),email,diasTrial,valor.movePointRight(2).intValueExact());
                String sessionId=identificador(response.path("id").asText());String url=response.path("url").asText();validarUrlStripe(url);
                store.registrar(id,estudioId,sessionId,url);return new LinkCheckout(id,url);
            }
            var body=new HashMap<String,Object>();body.put("external_reference",id);
            body.put("notification_url",publicUrl+"/assinatura/webhook");
            JsonNode response;
            if(metodo==Metodo.CARTAO){
                body.put("reason","Marcaê - assinatura mensal");body.put("payer_email",email);body.put("status","pending");
                body.put("back_url",publicUrl+"/?assinatura=retorno");
                body.put("auto_recurring",Map.of("frequency",1,"frequency_type","months","transaction_amount",atual.valor(),"currency_id","BRL"));
                response=gateway.criar("/preapproval",body,id);
            }else{
                body.put("items",List.of(Map.of("id","ink-manager-mensal","title","Marcaê - mensalidade por Pix","quantity",1,"unit_price",atual.valor(),"currency_id","BRL")));
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
    private boolean provedorDisponivel(){return provider.equals("STRIPE")?stripe.disponivel():gateway.disponivel();}
    private void estudoAbertoParaCheckout(long estudioId){
        var estudio=estudios.bloquear(estudioId).orElseThrow();
        if(estudio.isEncerrado())throw new ApiException(HttpStatus.GONE,"A conta deste estabelecimento foi encerrada.");
    }
    private void validarUrl(String url){
        try{var uri=URI.create(url);String host=uri.getHost();
            if(!"https".equals(uri.getScheme())||host==null||!(host.equals("www.mercadopago.com.br")||host.endsWith(".mercadopago.com.br")||host.equals("www.mercadopago.com")||host.endsWith(".mercadopago.com")))throw new IllegalArgumentException();
        }catch(RuntimeException invalid){throw new ApiException(HttpStatus.BAD_GATEWAY,"Checkout retornou um endereco invalido.");}
    }
    private void validarUrlStripe(String url){
        try{var uri=URI.create(url);if(!"https".equals(uri.getScheme())||!"checkout.stripe.com".equalsIgnoreCase(uri.getHost()))throw new IllegalArgumentException();}
        catch(RuntimeException invalid){throw new ApiException(HttpStatus.BAD_GATEWAY,"Stripe retornou um endereco de checkout invalido.");}
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
        if(checkout.provedor().equals("STRIPE")){sincronizarStripe(checkout);return;}
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
        var checkout=store.aberto(acesso.estudioId(),"CARTAO",provider).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Nenhuma recorrencia ativa."));
        if(provider.equals("STRIPE")){
            if(checkout.subscriptionId()==null)throw new ApiException(HttpStatus.CONFLICT,"Ainda nao ha uma recorrencia Stripe ativa para cancelar.");
            var subscription=stripe.cancelarSubscription(checkout.subscriptionId());
            String normalized=normalizarStatusStripe(subscription.path("status").asText());
            if(!normalized.equals("cancelled"))throw new ApiException(HttpStatus.BAD_GATEWAY,"Stripe ainda nao confirmou o cancelamento.");
            store.stripeSubscription(checkout.id(),checkout.estudioId(),checkout.subscriptionId(),normalized,clock.millis());return resumo();
        }
        var contrato=gateway.atualizar("/preapproval/"+identificador(checkout.providerId()),Map.of("status","cancelled"));
        if(!"cancelled".equals(contrato.path("status").asText()))throw new ApiException(HttpStatus.BAD_GATEWAY,"O provedor ainda nao confirmou o cancelamento.");
        processarContrato(contrato);return resumo();
    }
    private void sincronizarStripe(AssinaturaStore.Checkout checkout){
        if(!stripe.disponivel()||checkout.providerId()==null)return;
        var session=stripe.checkout(checkout.providerId());processarSessaoStripe(session,clock.millis());
        var atual=store.checkout(checkout.id()).orElse(checkout);String subscriptionId=atual.subscriptionId();
        if(subscriptionId==null)return;
        var subscription=stripe.subscription(identificador(subscriptionId));processarSubscriptionStripe(subscription,clock.millis());
        String cursor=null;
        for(int pageNo=0;pageNo<100;pageNo++){
            var page=stripe.invoicesPagas(subscriptionId,100,cursor);var data=page.path("data");
            for(JsonNode invoice:data)processarFaturaStripe(invoice,clock.millis());
            if(!page.path("has_more").asBoolean(false))return;
            String next=data.isEmpty()?"":data.get(data.size()-1).path("id").asText();
            if(next.isBlank()||next.equals(cursor))throw new ApiException(HttpStatus.BAD_GATEWAY,"Paginacao de faturas Stripe invalida.");
            cursor=next;
        }
        throw new ApiException(HttpStatus.BAD_GATEWAY,"A conciliacao Stripe excedeu o limite de paginas de faturas.");
    }
    public void reconciliarReembolsosStripe(){
        if(!stripe.disponivel())return;
        // Stripe retains Events for 30 days. A 29-day overlap leaves room for clock skew and
        // repeated delivery; refund handling is idempotent, so rescanning is safe.
        long since=clock.instant().minus(Duration.ofDays(29)).getEpochSecond();String cursor=null;
        for(int pageNo=0;pageNo<100;pageNo++){
            var page=stripe.reembolsosDesde(since,100,cursor);var data=page.path("data");
            for(JsonNode event:data){
                if(!"charge.refunded".equals(event.path("type").asText()))continue;
                processarReembolsoStripe(event.path("data").path("object"),event.path("created").asLong(0)*1000L,false);
            }
            if(!page.path("has_more").asBoolean(false))return;
            String next=data.isEmpty()?"":data.get(data.size()-1).path("id").asText();
            if(next.isBlank()||next.equals(cursor))throw new ApiException(HttpStatus.BAD_GATEWAY,"Paginacao de eventos Stripe invalida.");
            cursor=next;
        }
        throw new ApiException(HttpStatus.BAD_GATEWAY,"A conciliacao Stripe excedeu o limite de paginas de eventos.");
    }
    public void notificarStripe(JsonNode event){
        String type=event.path("type").asText();long updated=event.path("created").asLong(0)*1000L;JsonNode object=event.path("data").path("object");
        switch(type){
            case "checkout.session.completed","checkout.session.async_payment_succeeded" -> processarSessaoStripe(object,updated);
            case "invoice.paid" -> processarFaturaStripe(object,updated);
            case "invoice.payment_failed" -> atualizarStatusFaturaStripe(object,updated);
            case "charge.refunded" -> processarReembolsoStripe(object,updated,true);
            case "customer.subscription.updated","customer.subscription.deleted" -> {
                String subscriptionId=referenciaStripe(object.path("id"));
                if(!subscriptionId.isBlank()&&(store.checkoutPorSubscription(subscriptionId).isPresent()||!object.path("metadata").path("checkout_id").asText().isBlank()))
                    processarSubscriptionStripe(stripe.subscription(identificador(subscriptionId)),clock.millis());
            }
            default -> { }
        }
    }
    private void processarSessaoStripe(JsonNode session,long updatedAt){
        String metadataCheckoutId=session.path("metadata").path("checkout_id").asText();
        String clientReferenceId=session.path("client_reference_id").asText();
        String id=!metadataCheckoutId.isBlank()?metadataCheckoutId:clientReferenceId;
        if(id.isBlank())return;
        if(!metadataCheckoutId.isBlank()&&!clientReferenceId.isBlank()&&!metadataCheckoutId.equals(clientReferenceId)){
            log.error("Stripe checkout metadata and client reference point to different local checkouts; event ignored: metadata_checkout_id={}",metadataCheckoutId);return;
        }
        var checkout=store.checkout(id).orElse(null);
        if(checkout==null){
            if(metadataCheckoutId.isBlank())return;
            log.error("Stripe checkout session references an unknown local checkout: checkout_id={}",id);throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Checkout Stripe ainda nao pode ser associado a uma conta local.");
        }
        if(!checkout.provedor().equals("STRIPE"))return;
        if(!validarMetadataStripe(session.path("metadata"),checkout))return;
        if("subscription".equals(session.path("mode").asText())){
            String subscriptionId=referenciaStripe(session.path("subscription"));if(subscriptionId.isBlank()||subscriptionId.equals("null")){
                log.error("Stripe subscription checkout has no subscription reference: checkout_id={}",checkout.id());
                throw new ApiException(HttpStatus.BAD_GATEWAY,"Stripe ainda nao disponibilizou a assinatura do checkout.");
            }
            var subscription=stripe.subscription(identificador(subscriptionId));processarSubscriptionStripe(subscription,clock.millis());
            return;
        }
        if("paid".equals(session.path("payment_status").asText())){
            String paymentId=referenciaStripe(session.path("payment_intent"));
            if(paymentId.isBlank()||paymentId.equals("null")){
                log.error("Paid Stripe checkout has no PaymentIntent; payment not credited: checkout_id={}",checkout.id());
                throw new ApiException(HttpStatus.BAD_GATEWAY,"Stripe ainda nao disponibilizou o pagamento do checkout.");
            }
            registrarPagamentoStripe(checkout,"stripe_"+identificador(paymentId),session.path("amount_total").asInt(-1),session.path("currency").asText(),updatedAt);
        }
    }
    private void processarSubscriptionStripe(JsonNode subscription,long updatedAt){
        String id=subscription.path("id").asText();String checkoutId=subscription.path("metadata").path("checkout_id").asText();
        var checkout=!checkoutId.isBlank()?store.checkout(checkoutId).orElse(null):store.checkoutPorSubscription(id).orElse(null);
        if(checkout==null){
            if(!checkoutId.isBlank()){log.error("Stripe subscription metadata references an unknown local checkout: checkout_id={}",checkoutId);throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Assinatura Stripe ainda nao pode ser associada a uma conta local.");}
            return;
        }
        if(!checkout.provedor().equals("STRIPE")||!checkout.metodo().equals("CARTAO"))return;
        if(!validarMetadataStripe(subscription.path("metadata"),checkout))return;
        store.stripeSubscription(checkout.id(),checkout.estudioId(),identificador(id),normalizarStatusStripe(subscription.path("status").asText()),updatedAt);
    }
    private void processarFaturaStripe(JsonNode invoice,long updatedAt){
        String invoiceId=referenciaStripe(invoice.path("id"));if(invoiceId.isBlank())return;
        String subscriptionId=subscriptionId(invoice);if(subscriptionId.isBlank())return;
        var checkout=resolverCheckoutFatura(invoice,subscriptionId);if(checkout==null)return;
        if(!checkout.provedor().equals("STRIPE")||!checkout.metodo().equals("CARTAO"))return;
        if(invoice.path("paid_out_of_band").asBoolean(false))return;
        int amountPaid=invoice.path("amount_paid").asInt(-1);
        if(amountPaid<=0){log.info("Stripe invoice has no positive payment to credit: invoice_id={} checkout_id={}",invoiceId,checkout.id());return;}
        if(amountPaid!=checkout.valor().movePointRight(2).intValueExact()||!"brl".equalsIgnoreCase(invoice.path("currency").asText())){
            log.error("Stripe paid invoice does not match the configured subscription amount: invoice_id={} checkout_id={} amount_paid={} currency={}",invoiceId,checkout.id(),amountPaid,invoice.path("currency").asText());return;
        }
        var invoicePayments=invoicePaymentsPorInvoice(invoiceId);
        var paid=invoicePayments.stream().filter(p->"paid".equals(p.path("status").asText())).toList();
        if(paid.isEmpty())throw new ApiException(HttpStatus.BAD_GATEWAY,"Stripe ainda nao disponibilizou o pagamento da fatura.");
        if(paid.size()!=1){
            log.error("Stripe paid invoice uses an unsupported multi-payment allocation; access not credited: invoice_id={} checkout_id={} paid_payment_count={}",invoiceId,checkout.id(),paid.size());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Fatura Stripe tem mais de uma alocacao de pagamento e requer conciliacao.");
        }
        var payment=paid.getFirst();var paymentDetails=payment.path("payment");
        if(!"payment_intent".equals(paymentDetails.path("type").asText())||referenciaStripe(paymentDetails.path("payment_intent")).isBlank()
                ||payment.path("amount_paid").asInt(-1)!=amountPaid||!"brl".equalsIgnoreCase(payment.path("currency").asText())){
            log.error("Stripe paid invoice has an unsupported payment allocation; access not credited: invoice_id={} checkout_id={}",invoiceId,checkout.id());return;
        }
        registrarPagamentoStripe(checkout,"stripe_"+identificador(invoiceId),amountPaid,invoice.path("currency").asText(),updatedAt);
    }
    private void atualizarStatusFaturaStripe(JsonNode invoice,long updatedAt){
        String subscriptionId=invoice.path("subscription").asText();if(subscriptionId.isBlank()||subscriptionId.equals("null"))subscriptionId=invoice.path("parent").path("subscription_details").path("subscription").asText();
        if(!subscriptionId.isBlank()&&!subscriptionId.equals("null"))processarSubscriptionStripe(stripe.subscription(identificador(subscriptionId)),updatedAt);
    }
    private void processarReembolsoStripe(JsonNode charge,long updatedAt,boolean retryUnknown){
        if(!charge.path("refunded").asBoolean(false))return;
        boolean candidateCharge=charge.path("amount").asInt(-1)>0&&"brl".equalsIgnoreCase(charge.path("currency").asText());
        String paymentIntent=referenciaStripe(charge.path("payment_intent"));
        if(paymentIntent.isBlank()){
            if(retryUnknown&&candidateCharge)throw new ApiException(HttpStatus.BAD_GATEWAY,"Reembolso Stripe ainda nao pode ser associado a uma assinatura.");
            return;
        }
        String paymentIntentId=identificador(paymentIntent);
        var pixPayment=store.pagamento("stripe_"+paymentIntentId).orElse(null);
        if(pixPayment!=null){
            var pixCheckout=store.checkout(pixPayment.checkoutId()).orElse(null);
            if(pixCheckout!=null&&pixCheckout.provedor().equals("STRIPE")&&pixCheckout.metodo().equals("PIX")){
                revogarPagamentoStripe(pixCheckout,pixPayment.id(),updatedAt);return;
            }
        }
        var intent=stripe.paymentIntent(paymentIntentId);var metadata=intent.path("metadata");
        String metadataCheckoutId=metadata.path("checkout_id").asText();var metadataCheckout=store.checkout(metadataCheckoutId).orElse(null);
        if(metadataCheckout!=null&&metadataCheckout.provedor().equals("STRIPE")){
            if(!validarMetadataStripe(metadata,metadataCheckout))return;
            if(metadataCheckout.metodo().equals("PIX")){
                revogarPagamentoStripe(metadataCheckout,"stripe_"+paymentIntentId,updatedAt);return;
            }
        }
        var invoicePaymentsEncontrados=invoicePaymentsPorPaymentIntent(paymentIntentId);
        if(invoicePaymentsEncontrados.isEmpty()){
            if(retryUnknown&&(candidateCharge||metadataCheckout!=null))throw new ApiException(HttpStatus.BAD_GATEWAY,"Stripe ainda nao disponibilizou o vinculo do reembolso a fatura.");
            return;
        }
        boolean revogado=false;boolean permanentMismatch=false;
        for(var invoicePayment:invoicePaymentsEncontrados){
            if(!"paid".equals(invoicePayment.path("status").asText())
                    ||!"payment_intent".equals(invoicePayment.path("payment").path("type").asText())
                    ||!"brl".equalsIgnoreCase(invoicePayment.path("currency").asText()))continue;
            String invoiceId=referenciaStripe(invoicePayment.path("invoice"));if(invoiceId.isBlank())continue;
            var invoicePayments=invoicePaymentsPorInvoice(invoiceId);
            var paid=invoicePayments.stream().filter(p->"paid".equals(p.path("status").asText())).toList();
            if(paid.size()!=1){
                log.error("Stripe full refund maps to an invoice with unsupported multi-payment allocation; access was not revoked: invoice_id={} payment_intent_id={} paid_payment_count={}",invoiceId,paymentIntentId,paid.size());
                if(retryUnknown)throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Reembolso Stripe pertence a uma fatura com varias alocacoes e requer conciliacao.");
                permanentMismatch=true;continue;
            }
            var one=paid.getFirst();var details=one.path("payment");
            if(!"payment_intent".equals(details.path("type").asText())
                    ||!paymentIntentId.equals(referenciaStripe(details.path("payment_intent")))
                    ||one.path("amount_paid").asInt(-1)!=valor.movePointRight(2).intValueExact()
                    ||!"brl".equalsIgnoreCase(one.path("currency").asText())){
                log.error("Stripe full refund does not match a supported invoice payment; access was not revoked: invoice_id={} payment_intent_id={}",invoiceId,paymentIntentId);
                permanentMismatch=true;continue;
            }
            var local=store.pagamento("stripe_"+identificador(invoiceId)).orElse(null);
            var invoice=local==null?stripe.invoice(identificador(invoiceId)):null;
            String subscriptionId=local==null?subscriptionId(invoice):null;
            if(local==null&&(subscriptionId==null||subscriptionId.isBlank()))continue;
            var checkout=local==null?resolverCheckoutFatura(invoice,subscriptionId):store.checkout(local.checkoutId()).orElse(null);
            if(checkout==null||!checkout.provedor().equals("STRIPE")||!checkout.metodo().equals("CARTAO"))continue;
            if(metadataCheckout!=null&&!metadataCheckout.id().equals(checkout.id())){
                log.error("Stripe full refund metadata and invoice resolve to different checkouts: invoice_id={} payment_intent_id={} metadata_checkout_id={} invoice_checkout_id={}",invoiceId,paymentIntentId,metadataCheckout.id(),checkout.id());
                permanentMismatch=true;continue;
            }
            if(invoicePayment.path("amount_paid").asInt(-1)!=checkout.valor().movePointRight(2).intValueExact()
                    ||one.path("amount_paid").asInt(-1)!=checkout.valor().movePointRight(2).intValueExact()){
                log.error("Stripe full refund invoice amount does not match the local subscription price; access was not revoked: invoice_id={} checkout_id={}",invoiceId,checkout.id());
                permanentMismatch=true;continue;
            }
            revogarPagamentoStripe(checkout,"stripe_"+identificador(invoiceId),updatedAt);revogado=true;
        }
        if(!revogado&&!permanentMismatch&&retryUnknown&&(candidateCharge||metadataCheckout!=null))throw new ApiException(HttpStatus.BAD_GATEWAY,"Stripe ainda nao pode associar o reembolso a uma assinatura local.");
    }
    private void revogarPagamentoStripe(AssinaturaStore.Checkout checkout,String paymentId,long updatedAt){
        tx.executeWithoutResult(status->{
            estudios.bloquear(checkout.estudioId()).orElseThrow();var current=store.pagamento(paymentId).orElse(null);
            if(current!=null&&!current.checkoutId().equals(checkout.id()))throw new ApiException(HttpStatus.CONFLICT,"Reembolso Stripe associado a outra assinatura.");
            if(current!=null&&!current.aprovado())return;
            LocalDateTime approvedAt=current!=null&&current.aprovadoEm()!=null?current.aprovadoEm():LocalDateTime.ofInstant(clock.instant(),clock.getZone());
            store.gravarPagamento(new AssinaturaStore.Pagamento(paymentId,checkout.id(),checkout.estudioId(),false,current==null?null:current.fim(),approvedAt,updatedAt));
            if(checkout.metodo().equals("PIX"))store.status(checkout.id(),checkout.estudioId(),"refunded");
        });
    }
    private AssinaturaStore.Checkout resolverCheckoutFatura(JsonNode invoice,String subscriptionId){
        var checkout=store.checkoutPorSubscription(subscriptionId).orElse(null);
        if(checkout!=null)return checkout;
        var subscription=stripe.subscription(identificador(subscriptionId));
        String checkoutId=subscription.path("metadata").path("checkout_id").asText();
        if(checkoutId.isBlank())return null;
        checkout=store.checkout(checkoutId).orElse(null);
        if(checkout==null){log.error("Stripe invoice subscription metadata references an unknown local checkout: subscription_id={} checkout_id={}",subscriptionId,checkoutId);throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Fatura Stripe ainda nao pode ser associada a uma conta local.");}
        if(!checkout.provedor().equals("STRIPE")||!checkout.metodo().equals("CARTAO")||!validarMetadataStripe(subscription.path("metadata"),checkout))return null;
        processarSubscriptionStripe(subscription,clock.millis());
        return checkout;
    }
    private List<JsonNode> invoicePaymentsPorInvoice(String invoiceId){
        var found=new ArrayList<JsonNode>();String cursor=null;
        for(int pageNo=0;pageNo<100;pageNo++){
            var page=stripe.invoicePaymentsPorInvoice(invoiceId,100,cursor);var data=page.path("data");
            for(JsonNode item:data)found.add(item);
            if(!page.path("has_more").asBoolean(false))return found;
            String next=data.isEmpty()?"":data.get(data.size()-1).path("id").asText();
            if(next.isBlank()||next.equals(cursor))throw new ApiException(HttpStatus.BAD_GATEWAY,"Paginacao de pagamentos Stripe invalida.");
            cursor=next;
        }
        throw new ApiException(HttpStatus.BAD_GATEWAY,"A fatura Stripe excedeu o limite de paginas de pagamentos.");
    }
    private List<JsonNode> invoicePaymentsPorPaymentIntent(String paymentIntentId){
        var found=new ArrayList<JsonNode>();String cursor=null;
        for(int pageNo=0;pageNo<100;pageNo++){
            var page=stripe.invoicePaymentsPorPaymentIntent(paymentIntentId,100,cursor);var data=page.path("data");
            for(JsonNode item:data)found.add(item);
            if(!page.path("has_more").asBoolean(false))return found;
            String next=data.isEmpty()?"":data.get(data.size()-1).path("id").asText();
            if(next.isBlank()||next.equals(cursor))throw new ApiException(HttpStatus.BAD_GATEWAY,"Paginacao de pagamentos Stripe invalida.");
            cursor=next;
        }
        throw new ApiException(HttpStatus.BAD_GATEWAY,"O pagamento Stripe excedeu o limite de paginas.");
    }
    private String subscriptionId(JsonNode invoice){
        String subscription=referenciaStripe(invoice.path("subscription"));
        if(subscription.isBlank()||subscription.equals("null"))subscription=referenciaStripe(invoice.path("parent").path("subscription_details").path("subscription"));
        return subscription;
    }
    private String referenciaStripe(JsonNode value){return value.isObject()?value.path("id").asText():value.asText();}
    private void registrarPagamentoStripe(AssinaturaStore.Checkout checkout,String id,int amount,String currency,long updatedAt){
        if(!"brl".equalsIgnoreCase(currency)||amount!=checkout.valor().movePointRight(2).intValueExact()){
            log.error("Stripe payment does not match the configured subscription amount; access not credited: checkout_id={} amount={} currency={}",checkout.id(),amount,currency);return;
        }
        LocalDateTime approvedAt=LocalDateTime.ofInstant(Instant.ofEpochMilli(updatedAt>0?updatedAt:clock.millis()),clock.getZone());
        tx.executeWithoutResult(status->{
            estudios.bloquear(checkout.estudioId()).orElseThrow();var existing=store.pagamento(id);
            if(existing.isPresent()&&!existing.get().checkoutId().equals(checkout.id()))throw new ApiException(HttpStatus.CONFLICT,"Pagamento Stripe ja associado a outra assinatura.");
            if(existing.isPresent()&&existing.get().providerUpdatedAt()>updatedAt)return;
            if(existing.isPresent()&&!existing.get().aprovado()&&existing.get().aprovadoEm()!=null)return;
            store.gravarPagamento(new AssinaturaStore.Pagamento(id,checkout.id(),checkout.estudioId(),true,null,existing.map(AssinaturaStore.Pagamento::aprovadoEm).orElse(approvedAt),updatedAt));
            if(checkout.metodo().equals("PIX"))store.status(checkout.id(),checkout.estudioId(),"paid");
        });
    }
    private boolean validarMetadataStripe(JsonNode metadata,AssinaturaStore.Checkout checkout){
        String studio=metadata.path("studio_id").asText();
        if(studio.isBlank()||!studio.equals(Long.toString(checkout.estudioId()))){log.error("Stripe metadata studio is missing or does not match local checkout; event ignored: checkout_id={}",checkout.id());return false;}
        String method=metadata.path("checkout_id").asText();
        if(method.isBlank()||!method.equals(checkout.id())){log.error("Stripe metadata checkout is missing or does not match local checkout; event ignored: checkout_id={}",checkout.id());return false;}
        return true;
    }
    private String normalizarStatusStripe(String status){return switch(status){case "canceled"->"cancelled";default->status;};}
    private long providerUpdatedAt(JsonNode resource,String field){
        String value=resource.path(field).asText();
        return value.isBlank()?0:OffsetDateTime.parse(value).toInstant().toEpochMilli();
    }
    private String identificador(String id){if(id==null||!id.matches("[A-Za-z0-9_-]{1,100}"))throw new ApiException(HttpStatus.BAD_REQUEST,"Identificador de pagamento invalido.");return id;}
}

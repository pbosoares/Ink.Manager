package ink.manager.api;

import java.net.URI;
import java.net.http.*;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.*;
import ink.manager.api.service.RecuperacaoEmail;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
 "spring.datasource.url=${INK_TEST_DB_URL:jdbc:h2:mem:studio;DB_CLOSE_DELAY=-1}",
 "spring.datasource.driver-class-name=${INK_TEST_DB_DRIVER:org.h2.Driver}",
 "spring.datasource.username=${INK_TEST_DB_USER:sa}","spring.datasource.password=${INK_TEST_DB_PASSWORD:}",
 "jwt.secret=test-only-secret-with-more-than-32-bytes","studio.auth-attempts-per-minute=1000","studio.public-bookings-per-minute=1000","studio.public-queries-per-minute=1000","studio.time-zone=America/Sao_Paulo","billing.provider=mercadopago","billing.mp-webhook-secret=webhook-secret-for-test-only","billing.stripe-webhook-secret=stripe-webhook-secret-for-tests","billing.sync-initial-delay-ms=3600000","marcae.retencao.worker-ativo=false","marcae.retencao.lista-espera-horas=24","marcae.retencao.operacional-dias=30","MAIL_ENABLED=false","MARCAE_CLOSURE_MANIFEST_PATH=target/studio-test-manifest.log","MARCAE_CLOSURE_MANIFEST_KEY=closure-test-secret-with-more-than-32-bytes"})
class StudioIntegrationTest {
    record Conta(String token,long profissional,long estudio,long usuario,String email){}
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.security.oauth2.jwt.JwtEncoder encoder;
    @MockitoBean RecuperacaoEmail emailSender;
    @MockitoBean ink.manager.api.service.MercadoPagoGateway gateway;
    @MockitoBean ink.manager.api.service.StripeGateway stripe;
    @Autowired ink.manager.api.service.AssinaturaService assinaturaService;
    @Autowired ink.manager.api.service.AssinaturaConciliacao assinaturaConciliacao;
    @Autowired ink.manager.api.service.ExpurgoEstudioWorker expurgoEstudioWorker;
    @Autowired ink.manager.api.service.EmailOutboxService emailOutboxService;
    @Autowired ink.manager.api.repository.EstudioRepository estudioRepository;
    @Autowired ink.manager.api.service.ManifestoEncerramentoStore manifesto;
    @Autowired ink.manager.api.service.ManifestoRestauracaoGate gateRestauracao;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired Clock clock;
    final HttpClient client=HttpClient.newHttpClient();
    final ObjectMapper mapper=new ObjectMapper();

    @BeforeEach void clean(){
        try{java.nio.file.Files.deleteIfExists(java.nio.file.Path.of("target/studio-test-manifest.log"));}catch(java.io.IOException failure){throw new IllegalStateException(failure);}
        org.springframework.test.util.ReflectionTestUtils.setField(assinaturaService,"provider","MERCADOPAGO");
        for(String table:List.of("email_action_tokens","ofertas_lista_espera","lista_espera","email_outbox","retencoes_legais_financeiras","recebimentos_arquivo","expurgos_estudio","assinatura_pagamentos","assinatura_checkouts","recuperacoes_senha","recebimentos","marcacoes","bloqueios_agenda","horarios_trabalho","profissionais","cliente","usuarios","servicos","estudios")) jdbc.update("DELETE FROM "+table);
    }
    HttpResponse<String> request(String method,String path,String body,String token)throws Exception{
        var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(20));
        if(token!=null)b.header("Authorization","Bearer "+token);
        if(body!=null)b.header("Content-Type","application/json");
        return client.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode json(HttpResponse<String> response){return mapper.readTree(response.body());}
    String stringify(Object object){return mapper.writeValueAsString(object);}
    Conta conta(String nome)throws Exception{
        String email=UUID.randomUUID()+"@example.com";
        var created=request("POST","/auth/estudios",stringify(Map.of("nomeEstudio",nome,"nome","Responsavel","email",email,"senha","SenhaTeste123")),null);
        assertEquals(201,created.statusCode(),created.body());assertFalse(json(created).has("senha"));
        String token=login(email,"SenhaTeste123");
        var me=json(request("GET","/auth/me",null,token));
        long professional=json(request("GET","/profissionais",null,token)).get(0).get("id").asLong();
        return new Conta(token,professional,me.get("estudio").get("id").asLong(),me.get("usuario").get("id").asLong(),email);
    }
    String login(String email,String senha)throws Exception{
        var response=request("POST","/auth/login",stringify(Map.of("email",email,"senha",senha)),null);
        assertEquals(200,response.statusCode(),response.body());return json(response).get("token").asText();
    }
    long cliente(Conta conta)throws Exception{
        var response=request("POST","/clientes","{\"nome\":\"Maria\",\"telefone\":\"21999999999\"}",conta.token());
        assertEquals(201,response.statusCode(),response.body());return json(response).get("id").asLong();
    }
    Map<String,Object> reserva(long cliente,long profissional,String hora){
        return new HashMap<>(Map.of("cliente",Map.of("id",cliente),"profissionalId",profissional,"data","2099-10-15","horario",hora,"descricao","Rosa","status","AGENDADA","duracaoMinutos",60));
    }
    long profissional(Conta conta,String nome)throws Exception{
        int dia=LocalDate.parse("2099-10-15").getDayOfWeek().getValue();
        var body=Map.of("nome",nome,"ativo",true,"horarios",List.of(Map.of("diaSemana",dia,"inicio","09:00","fim","12:00"),Map.of("diaSemana",dia,"inicio","13:00","fim","18:00")));
        var response=request("POST","/profissionais",stringify(body),conta.token());
        assertEquals(201,response.statusCode(),response.body());return json(response).get("id").asLong();
    }
    JsonNode horarios(Conta conta,long profissional,int duracao)throws Exception{
        var response=request("GET","/profissionais/"+profissional+"/disponibilidade?data=2099-10-15&duracaoMinutos="+duracao,null,conta.token());
        assertEquals(200,response.statusCode(),response.body());return json(response).get("horarios");
    }
    boolean contem(JsonNode values,String value){for(var item:values)if(item.asText().equals(value))return true;return false;}

    @Test void cadastroAutonomoEArquivosPublicos()throws Exception{
        for(var path:List.of("/","/app.js","/team.js","/styles.css","/favicon.svg","/actuator/health"))assertEquals(200,request("GET",path,null,null).statusCode(),path);
        for(var path:List.of("/landing/visao-geral.png","/landing/agenda.png","/landing/reserva.png")){
            var image=request("GET",path,null,null);
            assertEquals(200,image.statusCode(),path);
            assertEquals("image/png",image.headers().firstValue("Content-Type").orElse(""),path);
        }
        for(var path:List.of("/landing/ilustracao-rotina.svg","/landing/login-agenda.svg")){
            var svg=request("GET",path,null,null);
            assertEquals(200,svg.statusCode(),path);
            assertEquals("image/svg+xml",svg.headers().firstValue("Content-Type").orElse(""),path);
        }
        assertEquals(401,request("GET","/clientes",null,null).statusCode());
        var conta=conta("Meu Estudio");
        assertEquals("Meu Estudio",json(request("GET","/auth/me",null,conta.token())).get("estudio").get("nome").asText());
        assertEquals(400,request("POST","/auth/estudios","{}",null).statusCode());
        assertEquals(401,request("POST","/auth/login",stringify(Map.of("email",conta.email(),"senha","errada")),null).statusCode());
        var edit=request("PUT","/estudio","{\"nome\":\"Nome Personalizado\",\"version\":0}",conta.token());
        assertEquals(200,edit.statusCode(),edit.body());
        assertEquals("Nome Personalizado",json(request("GET","/auth/me",null,conta.token())).get("estudio").get("nome").asText());
    }
    @Test void reutilizaClienteEVersaoProtegeEdicao()throws Exception{
        var conta=conta("Estudio A");long cliente=cliente(conta);var body=reserva(cliente,conta.profissional(),"10:00");
        var saved=request("POST","/marcacoes",stringify(body),conta.token());assertEquals(201,saved.statusCode(),saved.body());
        long id=json(saved).get("id").asLong();body.put("version",0);body.put("status","CONFIRMADA");
        assertEquals(200,request("PUT","/marcacoes/"+id,stringify(body),conta.token()).statusCode());
        assertEquals(409,request("PUT","/marcacoes/"+id,stringify(body),conta.token()).statusCode());
        assertEquals(201,request("POST","/marcacoes",stringify(reserva(cliente,conta.profissional(),"11:00")),conta.token()).statusCode());
        assertEquals(1,json(request("GET","/clientes",null,conta.token())).size());
        assertEquals(409,request("DELETE","/clientes/"+cliente,null,conta.token()).statusCode());
        body.put("descricao","x".repeat(256));assertEquals(400,request("POST","/marcacoes",stringify(body),conta.token()).statusCode());
    }
    @Test void cadastrosConjuntosFazemRollback()throws Exception{
        var conta=conta("Estudio");long cliente=cliente(conta);
        request("POST","/marcacoes",stringify(reserva(cliente,conta.profissional(),"10:00")),conta.token());
        var body=reserva(cliente,conta.profissional(),"10:30");body.put("cliente",Map.of("nome","Novo","telefone","21999999998"));
        assertEquals(409,request("POST","/marcacoes/com-cliente",stringify(body),conta.token()).statusCode());
        assertEquals(1,json(request("GET","/clientes",null,conta.token())).size());
        body.put("horario","12:00");var saved=request("POST","/marcacoes/com-cliente",stringify(body),conta.token());
        assertEquals(201,saved.statusCode(),saved.body());long id=json(saved).get("id").asLong();long linked=json(saved).get("cliente").get("id").asLong();
        body.put("version",0);body.put("status","CANCELADA");var edited=request("PUT","/marcacoes/"+id+"/com-cliente",stringify(body),conta.token());
        assertEquals(200,edited.statusCode(),edited.body());assertEquals(linked,json(edited).get("cliente").get("id").asLong());
        assertEquals(2,json(request("GET","/clientes",null,conta.token())).size());
    }
    @Test void reservaConcorrenteEAgendasIndependentes()throws Exception{
        var conta=conta("Estudio");long cliente=cliente(conta);var body=stringify(reserva(cliente,conta.profissional(),"14:00"));
        var tasks=new ArrayList<CompletableFuture<Integer>>();
        for(int i=0;i<2;i++)tasks.add(CompletableFuture.supplyAsync(()->{try{return request("POST","/marcacoes",body,conta.token()).statusCode();}catch(Exception e){throw new CompletionException(e);}}));
        assertEquals(List.of(201,409),tasks.stream().map(CompletableFuture::join).sorted().toList());
        long outro=profissional(conta,"Outro tatuador");
        assertEquals(201,request("POST","/marcacoes",stringify(reserva(cliente,outro,"14:00")),conta.token()).statusCode());
        assertEquals(400,request("POST","/marcacoes",stringify(reserva(cliente,outro,"23:30")),conta.token()).statusCode());
    }
    @Test void disponibilidadeRespeitaTurnosDuracaoEBloqueios()throws Exception{
        var conta=conta("Estudio");long p=profissional(conta,"Tatuador"),cliente=cliente(conta);
        assertTrue(contem(horarios(conta,p,60),"09:00:00"));
        assertFalse(contem(horarios(conta,p,60),"12:00:00"));
        assertEquals(409,request("POST","/marcacoes",stringify(reserva(cliente,p,"12:00")),conta.token()).statusCode());
        var saved=request("POST","/marcacoes",stringify(reserva(cliente,p,"10:00")),conta.token());assertEquals(201,saved.statusCode(),saved.body());
        assertFalse(contem(horarios(conta,p,90),"09:00:00"));assertTrue(contem(horarios(conta,p,60),"09:00:00"));
        String block="{\"inicio\":\"2099-10-15T13:00\",\"fim\":\"2099-10-15T18:00\",\"motivo\":\"Folga\"}";
        var blocked=request("POST","/profissionais/"+p+"/bloqueios",block,conta.token());assertEquals(201,blocked.statusCode(),blocked.body());
        assertFalse(contem(horarios(conta,p,60),"13:00:00"));
        assertEquals(409,request("POST","/profissionais/"+p+"/bloqueios",block.replace("13:00","10:00"),conta.token()).statusCode());
        assertEquals(204,request("DELETE","/profissionais/"+p+"/bloqueios/"+json(blocked).get("id").asLong(),null,conta.token()).statusCode());
        assertTrue(contem(horarios(conta,p,60),"13:00:00"));
        var update=reserva(cliente,p,"10:00");update.put("version",0);update.put("status","CANCELADA");
        assertEquals(200,request("PUT","/marcacoes/"+json(saved).get("id").asLong(),stringify(update),conta.token()).statusCode());
        assertTrue(contem(horarios(conta,p,60),"10:00:00"));
    }
    @Test void estudosNaoCompartilhamDadosNemAceitamVinculosCruzados()throws Exception{
        var a=conta("Estudio A");long clienteA=cliente(a);
        var booking=request("POST","/marcacoes",stringify(reserva(clienteA,a.profissional(),"10:00")),a.token());long bookingId=json(booking).get("id").asLong();
        var b=conta("Estudio B");assertEquals(0,json(request("GET","/clientes",null,b.token())).size());
        assertEquals(404,request("GET","/clientes/"+clienteA,null,b.token()).statusCode());
        assertEquals(404,request("GET","/marcacoes/"+bookingId,null,b.token()).statusCode());
        assertEquals(404,request("GET","/profissionais/"+a.profissional()+"/disponibilidade?data=2099-10-15",null,b.token()).statusCode());
        assertEquals(404,request("POST","/marcacoes",stringify(reserva(clienteA,b.profissional(),"10:00")),b.token()).statusCode());
        long clienteB=cliente(b);
        assertEquals(404,request("POST","/marcacoes",stringify(reserva(clienteB,a.profissional(),"10:00")),b.token()).statusCode());
        assertEquals(201,request("POST","/marcacoes",stringify(reserva(clienteB,b.profissional(),"10:00")),b.token()).statusCode());
        assertEquals(404,request("DELETE","/clientes/"+clienteA,null,b.token()).statusCode());
        assertEquals(404,request("POST","/equipe/"+a.usuario()+"/revogar-sessoes",null,b.token()).statusCode());
    }
    @Test void funcionarioAgendaEResponsavelRevogaAcesso()throws Exception{
        var conta=conta("Estudio");String email=UUID.randomUUID()+"@example.com";
        var created=request("POST","/equipe",stringify(Map.of("nome","Recepcao","email",email,"senha","SenhaTeste123")),conta.token());
        assertEquals(201,created.statusCode(),created.body());String token=login(email,"SenhaTeste123");long id=json(created).get("id").asLong();
        assertEquals(403,request("GET","/equipe",null,token).statusCode());
        assertEquals(403,request("PUT","/estudio","{\"nome\":\"Alteracao\",\"version\":0}",token).statusCode());
        long cliente=cliente(conta);assertEquals(201,request("POST","/marcacoes",stringify(reserva(cliente,conta.profissional(),"10:00")),token).statusCode());
        assertEquals(1,json(request("GET","/marcacoes",null,conta.token())).size());
        assertEquals(403,request("DELETE","/clientes/"+cliente,null,token).statusCode());
        assertEquals(200,request("PUT","/equipe/"+id,"{\"ativo\":false,\"version\":0}",conta.token()).statusCode());
        assertEquals(401,request("GET","/clientes",null,token).statusCode());
    }
    @Test void recuperacaoUnicaRevogaSessoesELogoutTambem()throws Exception{
        var conta=conta("Estudio");when(emailSender.disponivel()).thenReturn(true);
        assertEquals(200,request("POST","/auth/recuperar-senha",stringify(Map.of("email",conta.email())),null).statusCode());
        var link=org.mockito.ArgumentCaptor.forClass(String.class);verify(emailSender).enviar(eq(conta.email()),link.capture());
        String token=link.getValue().split("redefinir=")[1];
        assertNotEquals(token,jdbc.queryForObject("SELECT token_hash FROM recuperacoes_senha",String.class));
        String body=stringify(Map.of("token",token,"senha","NovaSenhaTeste123"));
        assertEquals(204,request("POST","/auth/redefinir-senha",body,null).statusCode());
        assertEquals(400,request("POST","/auth/redefinir-senha",body,null).statusCode());
        assertEquals(401,request("GET","/clientes",null,conta.token()).statusCode());
        String updated=login(conta.email(),"NovaSenhaTeste123");
        assertEquals(204,request("POST","/auth/logout",null,updated).statusCode());
        assertEquals(401,request("GET","/clientes",null,updated).statusCode());
    }

    HttpResponse<String> notificar(String type,String id,boolean valida)throws Exception{
        String ts=Long.toString(Instant.now().getEpochSecond()),requestId="webhook-test-request";
        var mac=javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec("webhook-secret-for-test-only".getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));
        String value="id:"+id.toLowerCase(Locale.ROOT)+";request-id:"+requestId+";ts:"+ts+";";
        String digest=HexFormat.of().formatHex(mac.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/assinatura/webhook?data.id="+id))
            .header("Content-Type","application/json").header("x-request-id",requestId)
            .header("x-signature","ts="+ts+",v1="+(valida?digest:"00".repeat(32)))
            .POST(HttpRequest.BodyPublishers.ofString(stringify(Map.of("type",type,"data",Map.of("id",id))))).build();
        return client.send(request,HttpResponse.BodyHandlers.ofString());
    }
    JsonNode pagamento(String id,String reference,String status){
        return mapper.readTree(stringify(Map.of("id",id,"external_reference",reference,"status",status,
            "currency_id","BRL","transaction_amount",new java.math.BigDecimal("29.90"),"transaction_amount_refunded",status.equals("refunded")?new java.math.BigDecimal("29.90"):java.math.BigDecimal.ZERO,
            "date_approved",OffsetDateTime.now(ZoneOffset.UTC).toString())));
    }
    @Test void pixExigeConfirmacaoAssinadaENaoDuplicaCredito()throws Exception{
        var conta=conta("Estudio assinante");var outro=conta("Outro estudio");
        jdbc.update("UPDATE estudios SET criado_em=? WHERE id=?",java.sql.Timestamp.valueOf(LocalDateTime.now().minusDays(40)),conta.estudio());
        assertEquals(402,request("POST","/clientes","{\"nome\":\"Novo\",\"telefone\":\"21999999999\"}",conta.token()).statusCode());
        assertEquals(200,request("GET","/clientes",null,conta.token()).statusCode());
        when(gateway.disponivel()).thenReturn(true);
        when(gateway.criar(eq("/checkout/preferences"),anyMap(),anyString())).thenReturn(mapper.readTree("{\"id\":\"pix-preference-1\",\"init_point\":\"https://www.mercadopago.com.br/checkout/pix-test\"}"));
        var checkout=request("POST","/assinatura/checkout","{\"metodo\":\"PIX\"}",conta.token());assertEquals(200,checkout.statusCode(),checkout.body());
        String reference=json(checkout).get("id").asText();
        assertEquals(reference,json(request("POST","/assinatura/checkout","{\"metodo\":\"PIX\"}",conta.token())).get("id").asText());
        verify(gateway,times(1)).criar(eq("/checkout/preferences"),anyMap(),anyString());
        when(gateway.obter("/v1/payments/1001")).thenReturn(pagamento("1001",reference,"approved"));
        assertEquals(401,notificar("payment","1001",false).statusCode());verify(gateway,never()).obter(anyString());
        assertEquals(200,notificar("payment","1001",true).statusCode());
        var pago=json(request("GET","/assinatura",null,conta.token()));assertEquals("ATIVA",pago.get("status").asText());
        assertEquals(200,notificar("payment","1001",true).statusCode());
        assertEquals(pago.get("pagoAte").asText(),json(request("GET","/assinatura",null,conta.token())).get("pagoAte").asText());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM assinatura_pagamentos WHERE estudio_id=?",Integer.class,conta.estudio()));
        assertTrue(json(request("GET","/assinatura",null,outro.token())).get("pagoAte").isNull());
        assertEquals(201,request("POST","/clientes","{\"nome\":\"Novo\",\"telefone\":\"21999999999\"}",conta.token()).statusCode());
        when(gateway.obter("/v1/payments/1001")).thenReturn(pagamento("1001",reference,"refunded"));
        assertEquals(200,notificar("payment","1001",true).statusCode());
        assertEquals("EXPIRADA",json(request("GET","/assinatura",null,conta.token())).get("status").asText());
        when(gateway.obter("/v1/payments/1001")).thenReturn(pagamento("1001",reference,"approved"));
        assertEquals(200,notificar("payment","1001",true).statusCode());
        assertEquals("EXPIRADA",json(request("GET","/assinatura",null,conta.token())).get("status").asText());
        when(gateway.obter("/v1/payments/1002")).thenReturn(pagamento("1002",reference,"approved"));
        assertEquals(200,notificar("payment","1002",true).statusCode());
        assertEquals("ATIVA",json(request("GET","/assinatura",null,conta.token())).get("status").asText());
    }
    @Test void cartaoSoLiberaPagamentoConfirmadoECancelamentoPreservaPeriodo()throws Exception{
        var conta=conta("Estudio cartao");when(gateway.disponivel()).thenReturn(true);
        when(gateway.criar(eq("/preapproval"),anyMap(),anyString())).thenReturn(mapper.readTree("{\"id\":\"card-contract-1\",\"init_point\":\"https://www.mercadopago.com.br/subscriptions/card-test\"}"));
        var checkout=request("POST","/assinatura/checkout","{\"metodo\":\"CARTAO\"}",conta.token());assertEquals(200,checkout.statusCode(),checkout.body());
        String reference=json(checkout).get("id").asText();
        var contrato=mapper.readTree(stringify(Map.of("id","card-contract-1","external_reference",reference,"status","authorized")));
        when(gateway.obter("/preapproval/card-contract-1")).thenReturn(contrato);
        assertEquals(200,notificar("subscription_preapproval","card-contract-1",true).statusCode());
        assertTrue(json(request("GET","/assinatura",null,conta.token())).get("pagoAte").isNull());
        when(gateway.obter("/authorized_payments/2001")).thenReturn(mapper.readTree("{\"preapproval_id\":\"card-contract-1\",\"payment\":{\"id\":\"3001\"}}"));
        when(gateway.obter("/v1/payments/3001")).thenReturn(pagamento("3001",reference,"approved"));
        assertEquals(200,notificar("subscription_authorized_payment","2001",true).statusCode());
        String until=json(request("GET","/assinatura",null,conta.token())).get("pagoAte").asText();
        when(gateway.atualizar(eq("/preapproval/card-contract-1"),anyMap())).thenReturn(mapper.readTree(stringify(Map.of("id","card-contract-1","external_reference",reference,"status","cancelled"))));
        var cancelled=request("POST","/assinatura/cancelar-recorrencia",null,conta.token());assertEquals(200,cancelled.statusCode(),cancelled.body());
        assertEquals(until,json(cancelled).get("pagoAte").asText());assertFalse(json(cancelled).get("cartaoEmRecorrencia").asBoolean());
    }
    @Test void pagamentoMenorNaoAtivaAssinatura()throws Exception{
        var conta=conta("Estudio");when(gateway.disponivel()).thenReturn(true);
        when(gateway.criar(eq("/checkout/preferences"),anyMap(),anyString())).thenReturn(mapper.readTree("{\"id\":\"pix-preference-2\",\"init_point\":\"https://www.mercadopago.com.br/checkout/pix-test\"}"));
        var checkout=request("POST","/assinatura/checkout","{\"metodo\":\"PIX\"}",conta.token());String reference=json(checkout).get("id").asText();
        var invalid=mapper.readTree(stringify(Map.of("id","4001","external_reference",reference,"status","approved","currency_id","BRL","transaction_amount",1)));
        when(gateway.obter("/v1/payments/4001")).thenReturn(invalid);
        assertEquals(400,notificar("payment","4001",true).statusCode());
        assertTrue(json(request("GET","/assinatura",null,conta.token())).get("pagoAte").isNull());
    }

    HttpResponse<String> webhookStripe(String raw)throws Exception{
        String timestamp=Long.toString(Instant.now().getEpochSecond());var mac=Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("stripe-webhook-secret-for-tests".getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        String signature=HexFormat.of().formatHex(mac.doFinal((timestamp+"."+raw).getBytes(StandardCharsets.UTF_8)));
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/assinatura/stripe-webhook"))
            .header("Content-Type","application/json").header("Stripe-Signature","t="+timestamp+",v1="+signature)
            .POST(HttpRequest.BodyPublishers.ofString(raw)).build();return client.send(request,HttpResponse.BodyHandlers.ofString());
    }
    @SuppressWarnings("unchecked")
    String stripeCheckoutForm(String metodo,String checkoutId)throws Exception{
        var http=mock(HttpClient.class);
        HttpResponse<String> providerResponse=(HttpResponse<String>)mock(HttpResponse.class);
        when(providerResponse.statusCode()).thenReturn(200);
        when(providerResponse.body()).thenReturn("{\"id\":\"cs_test_capture\",\"url\":\"https://checkout.stripe.com/c/pay/cs_test_capture\"}");
        var forms=new ArrayList<String>();
        doAnswer(invocation->{forms.add(requestFormBody(invocation.getArgument(0)));return providerResponse;})
            .when(http).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        var gateway=new ink.manager.api.service.StripeGateway("sk_test_only","whsec_test_only","https://ink.test");
        org.springframework.test.util.ReflectionTestUtils.setField(gateway,"client",http);
        gateway.criarCheckout(checkoutId,7,metodo,"trial@example.test",14,2990);
        assertEquals(1,forms.size());
        return java.net.URLDecoder.decode(forms.getFirst(),StandardCharsets.UTF_8);
    }
    String requestFormBody(HttpRequest request)throws Exception{
        var bytes=new ByteArrayOutputStream();var result=new CompletableFuture<String>();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>(){
            public void onSubscribe(Flow.Subscription subscription){subscription.request(Long.MAX_VALUE);}
            public void onNext(ByteBuffer item){byte[] chunk=new byte[item.remaining()];item.get(chunk);bytes.write(chunk,0,chunk.length);}
            public void onError(Throwable failure){result.completeExceptionally(failure);}
            public void onComplete(){result.complete(bytes.toString(StandardCharsets.UTF_8));}
        });
        return result.get(5,TimeUnit.SECONDS);
    }
    record StripeCardFixture(Conta conta,String checkoutId,String subscriptionId){}
    StripeCardFixture stripeCardFixture(String nome,String sessionId,String subscriptionId)throws Exception{
        var conta=conta(nome);org.springframework.test.util.ReflectionTestUtils.setField(assinaturaService,"provider","STRIPE");
        when(stripe.disponivel()).thenReturn(true);
        when(stripe.criarCheckout(anyString(),eq(conta.estudio()),eq("CARTAO"),eq(conta.email()),eq(14),eq(2990)))
            .thenReturn(mapper.readTree(stringify(Map.of("id",sessionId,"url","https://checkout.stripe.com/c/pay/"+sessionId))));
        var created=request("POST","/assinatura/checkout","{\"metodo\":\"CARTAO\"}",conta.token());
        assertEquals(200,created.statusCode(),created.body());String checkoutId=json(created).get("id").asText();
        var metadata=Map.of("checkout_id",checkoutId,"studio_id",Long.toString(conta.estudio()));
        when(stripe.subscription(subscriptionId)).thenReturn(mapper.readTree(stringify(Map.of("id",subscriptionId,"status","trialing","metadata",metadata))));
        String completed=stringify(Map.of("id","evt_"+sessionId,"type","checkout.session.completed","created",Instant.now().getEpochSecond(),
            "data",Map.of("object",Map.of("id",sessionId,"mode","subscription","subscription",subscriptionId,"metadata",metadata))));
        var linked=webhookStripe(completed);assertEquals(200,linked.statusCode(),linked.body());
        return new StripeCardFixture(conta,checkoutId,subscriptionId);
    }
    Map<String,Object> paidInvoicePayment(String id,String invoiceId,int amount,String paymentIntentId){
        return Map.of("id",id,"status","paid","invoice",invoiceId,"amount_paid",amount,"currency","brl",
            "payment",Map.of("type","payment_intent","payment_intent",paymentIntentId));
    }
    @Test void stripeGatewayCartaoComTrialSolicitaPaymentMethodCollectionAlways()throws Exception{
        String form=stripeCheckoutForm("CARTAO","checkout_card_trial");
        assertTrue(form.contains("payment_method_collection=always"),form);
        assertTrue(form.contains("subscription_data[trial_period_days]=14"),form);
    }
    @Test void stripeGatewayPixOmitePaymentMethodCollection()throws Exception{
        String form=stripeCheckoutForm("PIX","checkout_pix");
        assertFalse(form.contains("payment_method_collection"),form);
    }
    @Test void stripeCheckoutPagoSemPaymentIntentRetorna502SemCreditarNoReplay()throws Exception{
        var conta=conta("Stripe checkout pago sem PaymentIntent");org.springframework.test.util.ReflectionTestUtils.setField(assinaturaService,"provider","STRIPE");
        when(stripe.disponivel()).thenReturn(true);
        when(stripe.criarCheckout(anyString(),eq(conta.estudio()),eq("PIX"),eq(conta.email()),anyInt(),eq(2990)))
            .thenReturn(mapper.readTree("{\"id\":\"cs_test_paid_without_pi\",\"url\":\"https://checkout.stripe.com/c/pay/cs_test_paid_without_pi\"}"));
        var created=request("POST","/assinatura/checkout","{\"metodo\":\"PIX\"}",conta.token());assertEquals(200,created.statusCode(),created.body());
        String checkoutId=json(created).get("id").asText();var metadata=Map.of("checkout_id",checkoutId,"studio_id",Long.toString(conta.estudio()));
        String event=stringify(Map.of("id","evt_checkout_paid_without_pi","type","checkout.session.async_payment_succeeded","created",Instant.now().getEpochSecond(),
            "data",Map.of("object",Map.of("id","cs_test_paid_without_pi","mode","payment","payment_status","paid","client_reference_id",checkoutId,
                "currency","brl","amount_total",2990,"metadata",metadata))));
        for(int attempt=0;attempt<2;attempt++){
            var response=webhookStripe(event);assertEquals(502,response.statusCode(),response.body());
            assertTrue(json(request("GET","/assinatura",null,conta.token())).get("pagoAte").isNull());
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM assinatura_pagamentos WHERE estudio_id=?",Integer.class,conta.estudio()));
        }
        verify(stripe,never()).paymentIntent(anyString());
    }
    @Test void stripeInvoiceMultiAllocationReturns503WithoutCreditEvenOnReplay()throws Exception{
        var fixture=stripeCardFixture("Stripe invoice multi allocation","cs_test_invoice_multi","sub_test_invoice_multi");
        String invoiceId="in_test_invoice_multi";
        var allocations=List.of(paidInvoicePayment("inpay_multi_1",invoiceId,1495,"pi_multi_1"),paidInvoicePayment("inpay_multi_2",invoiceId,1495,"pi_multi_2"));
        when(stripe.invoicePaymentsPorInvoice(eq(invoiceId),eq(100),isNull()))
            .thenReturn(mapper.readTree(stringify(Map.of("data",allocations,"has_more",false))));
        String event=stringify(Map.of("id","evt_invoice_multi","type","invoice.paid","created",Instant.now().getEpochSecond(),
            "data",Map.of("object",Map.of("id",invoiceId,"subscription",fixture.subscriptionId(),"currency","brl","amount_paid",2990))));
        for(int attempt=0;attempt<2;attempt++){
            var response=webhookStripe(event);assertEquals(503,response.statusCode(),response.body());
            assertTrue(json(request("GET","/assinatura",null,fixture.conta().token())).get("pagoAte").isNull());
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM assinatura_pagamentos WHERE estudio_id=?",Integer.class,fixture.conta().estudio()));
        }
    }
    @Test void stripeRefundMultiAllocationReturns503WithoutRevokingOnReplay()throws Exception{
        var fixture=stripeCardFixture("Stripe refund multi allocation","cs_test_refund_multi","sub_test_refund_multi");
        String invoiceId="in_test_refund_multi",paymentIntentId="pi_test_refund_multi";
        var single=paidInvoicePayment("inpay_refund_single",invoiceId,2990,paymentIntentId);
        when(stripe.invoicePaymentsPorInvoice(eq(invoiceId),eq(100),isNull()))
            .thenReturn(mapper.readTree(stringify(Map.of("data",List.of(single),"has_more",false))));
        String paid=stringify(Map.of("id","evt_invoice_refund_multi_seed","type","invoice.paid","created",Instant.now().getEpochSecond(),
            "data",Map.of("object",Map.of("id",invoiceId,"subscription",fixture.subscriptionId(),"currency","brl","amount_paid",2990))));
        var paidResponse=webhookStripe(paid);assertEquals(200,paidResponse.statusCode(),paidResponse.body());
        String paidAte=json(request("GET","/assinatura",null,fixture.conta().token())).get("pagoAte").asText();assertFalse(paidAte.isBlank());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM assinatura_pagamentos WHERE estudio_id=? AND aprovado=TRUE",Integer.class,fixture.conta().estudio()));
        var allocations=List.of(paidInvoicePayment("inpay_refund_multi_1",invoiceId,1495,paymentIntentId),
            paidInvoicePayment("inpay_refund_multi_2",invoiceId,1495,"pi_other_allocation"));
        when(stripe.paymentIntent(paymentIntentId)).thenReturn(mapper.readTree(stringify(Map.of("id",paymentIntentId,"metadata",Map.of()))));
        when(stripe.invoicePaymentsPorPaymentIntent(eq(paymentIntentId),eq(100),isNull()))
            .thenReturn(mapper.readTree(stringify(Map.of("data",List.of(single),"has_more",false))));
        when(stripe.invoicePaymentsPorInvoice(eq(invoiceId),eq(100),isNull()))
            .thenReturn(mapper.readTree(stringify(Map.of("data",allocations,"has_more",false))));
        String refund=stringify(Map.of("id","evt_refund_multi","type","charge.refunded","created",Instant.now().getEpochSecond(),
            "data",Map.of("object",Map.of("id","ch_refund_multi","payment_intent",paymentIntentId,"refunded",true,"amount",2990,"amount_refunded",2990,"currency","brl"))));
        for(int attempt=0;attempt<2;attempt++){
            var response=webhookStripe(refund);assertEquals(503,response.statusCode(),response.body());
            assertEquals(paidAte,json(request("GET","/assinatura",null,fixture.conta().token())).get("pagoAte").asText());
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM assinatura_pagamentos WHERE estudio_id=? AND aprovado=TRUE",Integer.class,fixture.conta().estudio()));
        }
    }
    @Test void stripePixIsPaidOnlyBySignedConfirmationAndReplayDoesNotDuplicate()throws Exception{
        var conta=conta("Stripe Pix");org.springframework.test.util.ReflectionTestUtils.setField(assinaturaService,"provider","STRIPE");
        when(stripe.disponivel()).thenReturn(true);
        when(stripe.criarCheckout(anyString(),eq(conta.estudio()),eq("PIX"),eq(conta.email()),anyInt(),eq(2990)))
            .thenReturn(mapper.readTree("{\"id\":\"cs_test_pix_1\",\"url\":\"https://checkout.stripe.com/c/pay/cs_test_pix_1\"}"));
        var response=request("POST","/assinatura/checkout","{\"metodo\":\"PIX\"}",conta.token());assertEquals(200,response.statusCode(),response.body());
        String checkoutId=json(response).get("id").asText();
        var session=Map.of("id","cs_test_pix_1","mode","payment","payment_status","unpaid","client_reference_id",checkoutId,
            "currency","brl","amount_total",2990,"payment_intent","pi_test_pix_1","metadata",Map.of("checkout_id",checkoutId,"studio_id",Long.toString(conta.estudio())));
        String completed=stringify(Map.of("id","evt_test_completed","type","checkout.session.completed","created",Instant.now().getEpochSecond(),"data",Map.of("object",session)));
        String ts=Long.toString(Instant.now().getEpochSecond());var invalid=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/assinatura/stripe-webhook"))
            .header("Content-Type","application/json").header("Stripe-Signature","t="+ts+",v1=deadbeef").POST(HttpRequest.BodyPublishers.ofString(completed)).build();
        assertEquals(401,client.send(invalid,HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(200,webhookStripe(completed).statusCode());assertTrue(json(request("GET","/assinatura",null,conta.token())).get("pagoAte").isNull());
        var paidSession=new HashMap<>(session);paidSession.put("payment_status","paid");
        String paid=stringify(Map.of("id","evt_test_paid","type","checkout.session.async_payment_succeeded","created",Instant.now().getEpochSecond(),"data",Map.of("object",paidSession)));
        assertEquals(200,webhookStripe(paid).statusCode());String paidAte=json(request("GET","/assinatura",null,conta.token())).get("pagoAte").asText();assertFalse(paidAte.isBlank());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM assinatura_pagamentos WHERE estudio_id=?",Integer.class,conta.estudio()));
        assertEquals(200,webhookStripe(paid).statusCode());assertEquals(paidAte,json(request("GET","/assinatura",null,conta.token())).get("pagoAte").asText());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM assinatura_pagamentos WHERE estudio_id=?",Integer.class,conta.estudio()));
        when(stripe.paymentIntent("pi_test_pix_1")).thenReturn(mapper.readTree(stringify(Map.of("id","pi_test_pix_1","metadata",Map.of("checkout_id",checkoutId,"studio_id",Long.toString(conta.estudio()))))));
        String refund=stringify(Map.of("id","evt_test_refund","type","charge.refunded","created",Instant.now().getEpochSecond(),"data",Map.of("object",Map.of("id","ch_test_pix_1","payment_intent","pi_test_pix_1","refunded",true,"amount",2990,"amount_refunded",2990,"currency","brl"))));
        assertEquals(200,webhookStripe(refund).statusCode());assertTrue(json(request("GET","/assinatura",null,conta.token())).get("pagoAte").isNull());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM assinatura_pagamentos WHERE estudio_id=? AND aprovado=TRUE",Integer.class,conta.estudio()));
        assertEquals(200,webhookStripe(paid).statusCode());assertTrue(json(request("GET","/assinatura",null,conta.token())).get("pagoAte").isNull());
        verify(gateway,never()).criar(anyString(),anyMap(),anyString());
    }
    @Test void stripeCartaoComecaEmTrialSemCreditarAteFaturaPaga()throws Exception{
        var conta=conta("Stripe Cartao");org.springframework.test.util.ReflectionTestUtils.setField(assinaturaService,"provider","STRIPE");
        when(stripe.disponivel()).thenReturn(true);
        when(stripe.criarCheckout(anyString(),eq(conta.estudio()),eq("CARTAO"),eq(conta.email()),eq(14),eq(2990)))
            .thenReturn(mapper.readTree("{\"id\":\"cs_test_card_1\",\"url\":\"https://checkout.stripe.com/c/pay/cs_test_card_1\"}"));
        var response=request("POST","/assinatura/checkout","{\"metodo\":\"CARTAO\"}",conta.token());assertEquals(200,response.statusCode(),response.body());
        String checkoutId=json(response).get("id").asText();String subscriptionId="sub_test_card_1";
        when(stripe.subscription(subscriptionId)).thenReturn(mapper.readTree(stringify(Map.of("id",subscriptionId,"status","trialing","metadata",Map.of("checkout_id",checkoutId,"studio_id",Long.toString(conta.estudio()))))));
        String completed=stringify(Map.of("id","evt_test_card","type","checkout.session.completed","created",Instant.now().getEpochSecond(),"data",Map.of("object",Map.of("id","cs_test_card_1","mode","subscription","subscription",subscriptionId,"metadata",Map.of("checkout_id",checkoutId,"studio_id",Long.toString(conta.estudio()))))));
        assertEquals(200,webhookStripe(completed).statusCode());var summary=json(request("GET","/assinatura",null,conta.token()));assertTrue(summary.get("cartaoEmRecorrencia").asBoolean());assertTrue(summary.get("pagoAte").isNull());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM assinatura_pagamentos WHERE estudio_id=?",Integer.class,conta.estudio()));
        var invoicePayment=Map.of("id","inpay_test_card_1","status","paid","invoice","in_test_card_1","amount_paid",2990,"currency","brl","payment",Map.of("type","payment_intent","payment_intent","pi_test_card_1"));
        when(stripe.invoicePaymentsPorInvoice(eq("in_test_card_1"),eq(100),isNull())).thenReturn(mapper.readTree(stringify(Map.of("data",List.of(invoicePayment),"has_more",false))));
        String invoice=stringify(Map.of("id","evt_test_invoice","type","invoice.paid","created",Instant.now().getEpochSecond(),"data",Map.of("object",Map.of("id","in_test_card_1","subscription",subscriptionId,"currency","brl","amount_paid",2990))));
        var invoiceResponse=webhookStripe(invoice);assertEquals(200,invoiceResponse.statusCode(),invoiceResponse.body());assertFalse(json(request("GET","/assinatura",null,conta.token())).get("pagoAte").isNull());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM assinatura_pagamentos WHERE estudio_id=?",Integer.class,conta.estudio()));
    }

    @Test void stripeRefundAssociationTransientFailureReturnsRetryableStatus()throws Exception{
        var conta=conta("Stripe refund association retry");org.springframework.test.util.ReflectionTestUtils.setField(assinaturaService,"provider","STRIPE");
        when(stripe.disponivel()).thenReturn(true);
        String paymentIntentId="pi_refund_lookup_retry";
        when(stripe.paymentIntent(paymentIntentId)).thenReturn(mapper.readTree(stringify(Map.of("id",paymentIntentId,"metadata",Map.of()))));
        when(stripe.invoicePaymentsPorPaymentIntent(eq(paymentIntentId),eq(100),isNull()))
            .thenThrow(new ink.manager.api.exception.ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY,"Stripe lookup temporarily unavailable"));
        String refund=stringify(Map.of("id","evt_refund_lookup_retry","type","charge.refunded","created",Instant.now().getEpochSecond(),
            "data",Map.of("object",Map.of("id","ch_refund_lookup_retry","refunded",true,"payment_intent",paymentIntentId))));
        var response=webhookStripe(refund);
        assertTrue(response.statusCode()>=500&&response.statusCode()<600,"A transient Stripe association failure must be retried, not acknowledged: "+response.statusCode()+" "+response.body());
    }

    @Test void stripeConciliationRecoversMissedFullRefundWithoutDuplicatePayment()throws Exception{
        var conta=conta("Stripe reconciliation refund");org.springframework.test.util.ReflectionTestUtils.setField(assinaturaService,"provider","STRIPE");
        when(stripe.disponivel()).thenReturn(true);
        when(stripe.criarCheckout(anyString(),eq(conta.estudio()),eq("CARTAO"),eq(conta.email()),eq(14),eq(2990)))
            .thenReturn(mapper.readTree("{\"id\":\"cs_test_reconcile_refund\",\"url\":\"https://checkout.stripe.com/c/pay/cs_test_reconcile_refund\"}"));
        var checkoutResponse=request("POST","/assinatura/checkout","{\"metodo\":\"CARTAO\"}",conta.token());
        assertEquals(200,checkoutResponse.statusCode(),checkoutResponse.body());
        String checkoutId=json(checkoutResponse).get("id").asText(),subscriptionId="sub_test_reconcile_refund";
        var metadata=Map.of("checkout_id",checkoutId,"studio_id",Long.toString(conta.estudio()));
        var subscription=mapper.readTree(stringify(Map.of("id",subscriptionId,"status","active","metadata",metadata)));
        when(stripe.subscription(subscriptionId)).thenReturn(subscription);
        var session=mapper.readTree(stringify(Map.of("id","cs_test_reconcile_refund","mode","subscription","subscription",subscriptionId,"metadata",metadata)));
        when(stripe.checkout("cs_test_reconcile_refund")).thenReturn(session);
        String invoiceId="in_test_reconcile_refund",paymentIntentId="pi_test_reconcile_refund";
        var invoiceObject=Map.of("id",invoiceId,"subscription",subscriptionId,"currency","brl","amount_paid",2990);
        var invoicePayment=Map.of("id","inpay_test_reconcile_refund","status","paid","invoice",invoiceId,"amount_paid",2990,"currency","brl",
            "payment",Map.of("type","payment_intent","payment_intent",paymentIntentId));
        when(stripe.invoicePaymentsPorInvoice(eq(invoiceId),eq(100),isNull()))
            .thenReturn(mapper.readTree(stringify(Map.of("data",List.of(invoicePayment),"has_more",false))));
        when(stripe.invoicePaymentsPorPaymentIntent(eq(paymentIntentId),eq(100),isNull()))
            .thenReturn(mapper.readTree(stringify(Map.of("data",List.of(invoicePayment),"has_more",false))));
        when(stripe.invoicesPagas(eq(subscriptionId),eq(100),isNull()))
            .thenReturn(mapper.readTree(stringify(Map.of("data",List.of(invoiceObject),"has_more",false))));
        String paid=stringify(Map.of("id","evt_invoice_reconcile_seed","type","invoice.paid","created",Instant.now().getEpochSecond(),"data",Map.of("object",invoiceObject)));
        assertEquals(200,webhookStripe(paid).statusCode());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM assinatura_pagamentos WHERE estudio_id=? AND aprovado=TRUE",Integer.class,conta.estudio()));
        assertFalse(json(request("GET","/assinatura",null,conta.token())).get("pagoAte").isNull());
        when(stripe.paymentIntent(paymentIntentId)).thenReturn(mapper.readTree(stringify(Map.of("id",paymentIntentId,
            "latest_charge",Map.of("id","ch_test_reconcile_refund","refunded",true,"amount",2990,"amount_refunded",2990)))));
        var refundEvent=Map.of("id","evt_refund_reconcile_seed","type","charge.refunded","created",Instant.now().getEpochSecond(),"data",Map.of("object",Map.of("id","ch_test_reconcile_refund","payment_intent",paymentIntentId,"refunded",true,"amount",2990,"amount_refunded",2990,"currency","brl")));
        when(stripe.reembolsosDesde(anyLong(),eq(100),isNull()))
            .thenReturn(mapper.readTree(stringify(Map.of("data",List.of(refundEvent),"has_more",false))));

        assinaturaConciliacao.conciliar();

        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM assinatura_pagamentos WHERE estudio_id=?",Integer.class,conta.estudio()),"Reconciliation must preserve a single payment row");
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM assinatura_pagamentos WHERE estudio_id=? AND aprovado=TRUE",Integer.class,conta.estudio()),"A missed full refund must revoke the paid credit");
        assertTrue(json(request("GET","/assinatura",null,conta.token())).get("pagoAte").isNull());
    }
    // Contract tests for docs/MARCAE-CONTRATOS.md. HTTP is the assertion boundary;
    // JDBC only prepares historical dates that cannot be booked through the UI.
    record Publico(Conta conta, String link, long servico, long profissional, LocalDate dia) {}

    JsonNode servico(Conta owner, String nome, String preco, int minutos) throws Exception {
        var response=request("POST","/servicos",stringify(Map.of("nome",nome,"preco",new java.math.BigDecimal(preco),"duracaoMinutos",minutos,"ativo",true)),owner.token());
        assertEquals(201,response.statusCode(),response.body());
        return json(response);
    }
    String linkPublico(Conta owner) throws Exception {
        var response=request("GET","/servicos/link",null,owner.token());
        assertEquals(200,response.statusCode(),response.body());
        String caminho=json(response).get("caminho").asText();
        assertTrue(caminho.startsWith("/agendar.html?local="),caminho);
        return caminho.substring("/agendar.html?local=".length());
    }
    long profissionalPublico(Conta owner, LocalDate dia, String nome) throws Exception {
        var turnos=List.of(Map.of("diaSemana",dia.getDayOfWeek().getValue(),"inicio","09:00","fim","12:00"),
            Map.of("diaSemana",dia.getDayOfWeek().getValue(),"inicio","13:00","fim","18:00"));
        var response=request("POST","/profissionais",stringify(Map.of("nome",nome,"ativo",true,"horarios",turnos)),owner.token());
        assertEquals(201,response.statusCode(),response.body());
        return json(response).get("id").asLong();
    }
    Publico publico(String nome) throws Exception {
        var owner=conta(nome);String link=linkPublico(owner);
        var catalogo=request("GET","/publico/"+link,null,null);
        assertEquals(200,catalogo.statusCode(),catalogo.body());
        LocalDate dia=LocalDate.parse(json(catalogo).get("hoje").asText()).plusDays(7);
        long p=profissionalPublico(owner,dia,"Profissional publico");
        long s=servico(owner,"Corte simples","25.90",30).get("id").asLong();
        return new Publico(owner,link,s,p,dia);
    }
    Map<String,Object> pedido(Publico p,String hora) {
        return new HashMap<>(Map.of("chave",UUID.randomUUID().toString(),"servicoId",p.servico(),
            "profissionalId",p.profissional(),"data",p.dia().toString(),"horario",hora,
            "nome","Cliente publico","telefone","21999998888","email","cliente."+UUID.randomUUID()+"@example.com","avisoPrivacidadeLido",true));
    }
    HttpResponse<String> reservar(Publico p,Map<String,Object> body) throws Exception {
        return request("POST","/publico/"+p.link()+"/reservas",stringify(body),null);
    }
    HttpResponse<String> slots(Publico p,long s,long profissional,LocalDate dia) throws Exception {
        return request("GET","/publico/"+p.link()+"/horarios?servicoId="+s+"&profissionalId="+profissional+"&data="+dia,null,null);
    }
    JsonNode slots(Publico p) throws Exception {
        var response=slots(p,p.servico(),p.profissional(),p.dia());
        assertEquals(200,response.statusCode(),response.body());
        assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        return json(response).get("horarios");
    }
    void dinheiro(String esperado,JsonNode valor) {
        assertNotNull(valor);
        assertEquals(0,new java.math.BigDecimal(esperado).compareTo(new java.math.BigDecimal(valor.asText())),valor.toString());
    }
    List<HttpResponse<String>> concorrentes(Callable<HttpResponse<String>> a,Callable<HttpResponse<String>> b) throws Exception {
        var executor=Executors.newFixedThreadPool(2);var inicio=new CountDownLatch(1);
        try {
            var fa=executor.submit(()->{assertTrue(inicio.await(10,TimeUnit.SECONDS));return a.call();});
            var fb=executor.submit(()->{assertTrue(inicio.await(10,TimeUnit.SECONDS));return b.call();});
            inicio.countDown();
            return List.of(fa.get(30,TimeUnit.SECONDS),fb.get(30,TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }
    String funcionario(Conta owner) throws Exception {
        String email=UUID.randomUUID()+"@example.com";
        var response=request("POST","/equipe",stringify(Map.of("nome","Equipe","email",email,"senha","SenhaTeste123")),owner.token());
        assertEquals(201,response.statusCode(),response.body());
        return login(email,"SenhaTeste123");
    }
    JsonNode marcacaoParaReceber(Conta owner,String hora,String status) throws Exception {
        var body=reserva(cliente(owner),owner.profissional(),hora);
        var created=request("POST","/marcacoes",stringify(body),owner.token());
        assertEquals(201,created.statusCode(),created.body());
        long id=json(created).get("id").asLong();
        jdbc.update("UPDATE marcacoes SET data=?,status=? WHERE id=?",java.sql.Date.valueOf("2020-01-01"),status,id);
        var loaded=request("GET","/marcacoes/"+id,null,owner.token());
        assertEquals(200,loaded.statusCode(),loaded.body());return json(loaded);
    }
    HttpResponse<String> receber(Conta owner,long id,String valor) throws Exception {
        return request("POST","/recebimentos/marcacoes/"+id,"{\"valor\":"+valor+"}",owner.token());
    }
    JsonNode resumo(Conta owner,String data) throws Exception {
        var response=request("GET","/recebimentos"+(data==null?"":"?data="+data),null,owner.token());
        assertEquals(200,response.statusCode(),response.body());return json(response);
    }

    HttpResponse<String> fechar(Conta owner,String confirmacao)throws Exception{
        return request("POST","/estudio/encerramento",stringify(Map.of("confirmacao",confirmacao)),owner.token());
    }
    ink.manager.api.service.ExpurgoEstudioWorker worker(){
        return new ink.manager.api.service.ExpurgoEstudioWorker(jdbc,estudioRepository,transactionManager,clock,true,20,manifesto,gateRestauracao);
    }
    void tornarFilaVencida(long estudio){
        var before=java.time.OffsetDateTime.ofInstant(clock.instant().minusSeconds(1),ZoneOffset.UTC);
        jdbc.update("UPDATE expurgos_estudio SET lista_espera_ate=?,proxima_tentativa_em=NULL WHERE estudio_id=?",before,estudio);
    }
    void tornarOperacionalVencido(long estudio){
        var before=java.time.OffsetDateTime.ofInstant(clock.instant().minusSeconds(1),ZoneOffset.UTC);
        jdbc.update("UPDATE expurgos_estudio SET operacional_ate=?,proxima_tentativa_em=NULL WHERE estudio_id=?",before,estudio);
    }
    String inserirCheckout(Conta owner,String metodo,String status){
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO assinatura_checkouts(id,estudio_id,metodo,valor,status,criado_em,provedor) VALUES (?,?,?,?,?,?,?)",
            id,owner.estudio(),metodo,new java.math.BigDecimal("29.90"),status,java.sql.Timestamp.from(clock.instant()),"MERCADOPAGO");
        return id;
    }
    String inserirLedger(Conta owner,String status){
        String checkout=inserirCheckout(owner,"PIX",status);String payment="purge-payment-"+UUID.randomUUID();
        jdbc.update("INSERT INTO assinatura_pagamentos(id,checkout_id,estudio_id,aprovado,fim,aprovado_em,provider_updated_at) VALUES (?,?,?,TRUE,?,?,?)",
            payment,checkout,owner.estudio(),java.sql.Timestamp.from(clock.instant()),java.sql.Timestamp.from(clock.instant()),1L);
        return payment;
    }
    void inserirOutbox(Conta owner,String state,String suffix){
        var now=java.time.OffsetDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);
        var lease=state.equals("ENVIANDO")?now.plusMinutes(2):null;
        var leaseToken=lease==null?null:UUID.randomUUID().toString();
        jdbc.update("INSERT INTO email_outbox(estudio_id,destinatario,tipo,chave_idempotencia,disponivel_em,referencia,dados_json,estado,tentativas,lease_ate,lease_token,criado_em) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",owner.estudio(),"purge-"+suffix+"@example.test","LEMBRETE_24H","purge-"+suffix,
            now,"purge-"+suffix,"{}",state,state.equals("ENVIANDO")?1:0,lease,leaseToken,now);
    }
    void entrarNaLista(Publico p,String hora,String email)throws Exception{
        var inicio=p.dia().atTime(LocalTime.parse(hora)).atZone(ZoneId.of("America/Sao_Paulo")).toOffsetDateTime();
        var pedido=new HashMap<String,Object>();pedido.put("chaveIdempotencia",UUID.randomUUID());pedido.put("servicoId",p.servico());
        pedido.put("profissionalId",p.profissional());pedido.put("inicio",inicio.toString());pedido.put("fim",inicio.plusMinutes(30).toString());
        pedido.put("nome","Contato expurgo");pedido.put("email",email);pedido.put("telefone","21999998888");pedido.put("avisoPrivacidadeLido",true);
        var response=request("POST","/publico/"+p.link()+"/lista-espera",stringify(pedido),null);
        assertEquals(200,response.statusCode(),response.body());
    }
    long cancelarReservaQueGeraOferta(Publico p,String hora,String email)throws Exception{
        entrarNaLista(p,hora,email);var created=reservar(p,pedido(p,hora));assertEquals(201,created.statusCode(),created.body());
        var agenda=json(request("GET","/marcacoes",null,p.conta().token()));
        var saved=java.util.stream.StreamSupport.stream(agenda.spliterator(),false)
            .filter(m->m.get("data").asText().equals(p.dia().toString())&&m.get("horario").asText().startsWith(hora)).findFirst().orElseThrow();
        long id=saved.get("id").asLong();var cancel=new HashMap<String,Object>();
        cancel.put("cliente",Map.of("id",saved.get("cliente").get("id").asLong()));cancel.put("data",saved.get("data").asText());
        cancel.put("horario",saved.get("horario").asText());cancel.put("descricao",saved.get("descricao").asText());cancel.put("status","CANCELADA");
        cancel.put("duracaoMinutos",saved.get("duracaoMinutos").asInt());cancel.put("version",saved.get("version").asLong());cancel.put("profissionalId",p.profissional());
        var response=request("PUT","/marcacoes/"+id,stringify(cancel),p.conta().token());assertEquals(200,response.statusCode(),response.body());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM ofertas_lista_espera WHERE estudio_id=?",Integer.class,p.conta().estudio()));
        return id;
    }
    String inserirTokenAcao(long estudio,long marcacao)throws Exception{
        byte[] bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);String token=HexFormat.of().formatHex(bytes);
        String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        var now=java.time.OffsetDateTime.ofInstant(clock.instant(),ZoneOffset.UTC);
        jdbc.update("INSERT INTO email_action_tokens(token_hash,estudio_id,marcacao_id,acao,criado_em,expira_em) VALUES (?,?,?,?,?,?)",
            hash,estudio,marcacao,"CONFIRMAR",now,now.plusDays(1));
        return token;
    }
    String inserirTokenOferta(long estudio,long oferta)throws Exception{
        byte[] bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);String token=HexFormat.of().formatHex(bytes);
        String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        var prazo=java.time.OffsetDateTime.ofInstant(clock.instant().plus(Duration.ofDays(1)),ZoneOffset.UTC);
        jdbc.update("UPDATE ofertas_lista_espera SET estado='OFERTADA',token_hash=?,token_prazo=? WHERE id=? AND estudio_id=?",hash,prazo,oferta,estudio);
        return token;
    }
    Instant instanteBanco(Object value){
        if(value instanceof Instant instant)return instant;
        if(value instanceof java.time.OffsetDateTime offset)return offset.toInstant();
        if(value instanceof java.sql.Timestamp timestamp)return timestamp.toInstant();
        throw new AssertionError("Timestamp type inesperado: "+(value==null?"null":value.getClass().getName()));
    }
    java.time.OffsetDateTime utc(Instant value){return java.time.OffsetDateTime.ofInstant(value,ZoneOffset.UTC);}
    Set<String> tabelasReferenciadas(String tabela){
        return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Set<String>>)connection->{
            var referenced=new HashSet<String>();var metadata=connection.getMetaData();
            for(String candidate:List.of(tabela,tabela.toUpperCase(Locale.ROOT))){
                try(var keys=metadata.getImportedKeys(connection.getCatalog(),null,candidate)){
                    while(keys.next())referenced.add(keys.getString("PKTABLE_NAME").toLowerCase(Locale.ROOT));
                }
            }
            return referenced;
        });
    }
    void assertSegundosEntre(Instant inicio,Instant fim,long esperado,long tolerancia){
        assertTrue(Math.abs(Duration.between(inicio,fim).toSeconds()-esperado)<=tolerancia,
            "Intervalo esperado: "+esperado+"s, atual: "+Duration.between(inicio,fim).toSeconds()+"s");
    }

    @Test void catalogoPublicoLinkFixoESemContatos() throws Exception {
        var p=publico("Marcae publico");
        var hidden=servico(p.conta(),"Arquivado","10.00",15);
        var archived=request("PUT","/servicos/"+hidden.get("id").asLong(),stringify(Map.of("nome","Arquivado","preco",10,"duracaoMinutos",15,"ativo",false,"version",hidden.get("version").asLong())),p.conta().token());
        assertEquals(200,archived.statusCode(),archived.body());
        var catalogo=request("GET","/publico/"+p.link(),null,null);
        assertEquals(200,catalogo.statusCode(),catalogo.body());
        assertTrue(catalogo.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        var data=json(catalogo);assertEquals(7,data.size());assertEquals("Marcae publico",data.get("nome").asText());
        assertEquals("America/Sao_Paulo",data.get("fusoHorario").asText());assertEquals(1,data.get("servicos").size());
        var s=data.get("servicos").get(0);assertEquals(4,s.size());assertEquals(p.servico(),s.get("id").asLong());dinheiro("25.90",s.get("preco"));
        for(var profissional:data.get("profissionais")){assertEquals(2,profissional.size());assertTrue(profissional.has("id"));assertTrue(profissional.has("nome"));}
        for(var campo:List.of("telefone","email","clientes","reservas","version","estudioId"))assertFalse(catalogo.body().contains("\""+campo+"\""),catalogo.body());
        var me=json(request("GET","/auth/me",null,p.conta().token()));
        var edit=request("PUT","/estudio",stringify(Map.of("nome","Nome novo","version",me.get("estudio").get("version").asLong())),p.conta().token());
        assertEquals(200,edit.statusCode(),edit.body());assertEquals(p.link(),linkPublico(p.conta()));
        assertEquals("Nome novo",json(request("GET","/publico/"+p.link(),null,null)).get("nome").asText());
        assertEquals(404,request("GET","/publico/inexistente",null,null).statusCode());
    }
    @Test void reservaPublicaSemContaRespostaMinimaESnapshotServidor() throws Exception {
        var p=publico("Reserva sem conta");
        int usuarios=jdbc.queryForObject("SELECT count(*) FROM usuarios",Integer.class);
        var body=pedido(p,"09:15");body.put("duracaoMinutos",1);body.put("preco",0);body.put("servicoNome","Nome adulterado");
        var created=reservar(p,body);assertEquals(201,created.statusCode(),created.body());
        assertTrue(created.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        var confirm=json(created);assertEquals(8,confirm.size());assertEquals(30,confirm.get("duracaoMinutos").asInt());
        for(var campo:List.of("protocolo","servico","profissional","data","horario","fusoHorario","preco","duracaoMinutos"))assertTrue(confirm.has(campo),campo);
        for(var campo:List.of("telefone","email","cliente","nome","clienteId","marcacaoId"))assertFalse(confirm.has(campo),campo);
        assertEquals("Corte simples",confirm.get("servico").asText());dinheiro("25.90",confirm.get("preco"));
        assertEquals(usuarios,jdbc.queryForObject("SELECT count(*) FROM usuarios",Integer.class));
        var agenda=json(request("GET","/marcacoes",null,p.conta().token()));assertEquals(1,agenda.size());
        long id=agenda.get(0).get("id").asLong();assertEquals(30,agenda.get(0).get("duracaoMinutos").asInt());
        assertEquals("Corte simples",agenda.get(0).get("servicoNome").asText());dinheiro("25.90",agenda.get(0).get("precoPrevisto"));
        var s=json(request("GET","/servicos",null,p.conta().token())).get(0);
        var edit=request("PUT","/servicos/"+p.servico(),stringify(Map.of("nome","Servico novo","preco",99,"duracaoMinutos",60,"ativo",true,"version",s.get("version").asLong())),p.conta().token());
        assertEquals(200,edit.statusCode(),edit.body());
        var saved=json(request("GET","/marcacoes/"+id,null,p.conta().token()));
        assertEquals(30,saved.get("duracaoMinutos").asInt());assertEquals("Corte simples",saved.get("servicoNome").asText());dinheiro("25.90",saved.get("precoPrevisto"));
        var replay=reservar(p,body);assertEquals(201,replay.statusCode(),replay.body());assertEquals(confirm,json(replay));
        body.put("telefone","21911112222");assertEquals(409,reservar(p,body).statusCode());
        assertEquals(1,json(request("GET","/clientes",null,p.conta().token())).size());
        assertEquals(1,json(request("GET","/marcacoes",null,p.conta().token())).size());
        jdbc.update("UPDATE marcacoes SET data=? WHERE id=?",java.sql.Date.valueOf("2020-01-01"),id);
        var recebido=receber(p.conta(),id,"19.90");assertEquals(200,recebido.statusCode(),recebido.body());
        assertEquals("Corte simples",json(recebido).get("servico").asText());dinheiro("19.90",json(recebido).get("valor"));
    }
    @Test void reservaPublicaRespeitaTrintaMinutosGradeEFronteira() throws Exception {
        var p=publico("Grade");assertTrue(contem(slots(p),"09:15:00"));
        var adulterada=request("GET","/publico/"+p.link()+"/horarios?servicoId="+p.servico()+"&profissionalId="+p.profissional()+"&data="+p.dia()+"&duracaoMinutos=1",null,null);
        assertEquals(200,adulterada.statusCode(),adulterada.body());assertEquals(30,json(adulterada).get("duracaoMinutos").asInt());
        assertFalse(contem(json(adulterada).get("horarios"),"11:45:00"));
        assertEquals(201,reservar(p,pedido(p,"09:15")).statusCode());
        var livres=slots(p);assertFalse(contem(livres,"09:00:00"));assertFalse(contem(livres,"09:15:00"));assertFalse(contem(livres,"09:30:00"));assertTrue(contem(livres,"09:45:00"));
        assertEquals(409,reservar(p,pedido(p,"09:20")).statusCode());
        assertEquals(409,reservar(p,pedido(p,"09:30")).statusCode());
        assertEquals(201,reservar(p,pedido(p,"09:45")).statusCode());
        var foraGrade=pedido(p,"10:05");assertEquals(409,reservar(p,foraGrade).statusCode());
    }
    @Test void publicoRespeitaTurnoIntervaloBloqueioEJanelaDeDatas() throws Exception {
        var p=publico("Expediente");
        var block=request("POST","/profissionais/"+p.profissional()+"/bloqueios",stringify(Map.of("inicio",p.dia()+"T14:00","fim",p.dia()+"T15:00","motivo","Folga privada")),p.conta().token());
        assertEquals(201,block.statusCode(),block.body());
        var livres=slots(p);assertTrue(contem(livres,"11:30:00"));assertFalse(contem(livres,"11:45:00"));assertFalse(contem(livres,"12:00:00"));
        assertFalse(contem(livres,"13:45:00"));assertFalse(contem(livres,"14:00:00"));assertTrue(contem(livres,"15:00:00"));
        for(var hora:List.of("08:45","11:45","12:00","13:45","14:00","17:45"))assertEquals(409,reservar(p,pedido(p,hora)).statusCode(),hora);
        assertEquals(201,reservar(p,pedido(p,"15:00")).statusCode());
        var catalogo=json(request("GET","/publico/"+p.link(),null,null));LocalDate hoje=LocalDate.parse(catalogo.get("hoje").asText());
        for(var dia:List.of(hoje.minusDays(1),hoje.plusDays(366))){
            var body=pedido(p,"09:00");body.put("data",dia.toString());
            assertEquals(400,reservar(p,body).statusCode());assertEquals(400,slots(p,p.servico(),p.profissional(),dia).statusCode());
        }
        assertEquals(200,slots(p,p.servico(),p.profissional(),hoje.plusDays(365)).statusCode());
    }
    @Test void servicosEProfissionaisSaoIsoladosEntreEstudios() throws Exception {
        var a=publico("A");var b=publico("B");
        var lista=json(request("GET","/servicos",null,b.conta().token()));assertEquals(1,lista.size());assertEquals(b.servico(),lista.get(0).get("id").asLong());
        assertEquals(404,slots(a,b.servico(),a.profissional(),a.dia()).statusCode());
        assertEquals(404,slots(a,a.servico(),b.profissional(),a.dia()).statusCode());
        var body=pedido(a,"09:00");body.put("servicoId",b.servico());assertEquals(404,reservar(a,body).statusCode());
        body=pedido(a,"09:00");body.put("profissionalId",b.profissional());assertEquals(404,reservar(a,body).statusCode());
        var edit=Map.of("nome","Invasao","preco",1,"duracaoMinutos",15,"ativo",true,"version",0);
        assertEquals(404,request("PUT","/servicos/"+a.servico(),stringify(edit),b.conta().token()).statusCode());
        var archived=request("PUT","/servicos/"+a.servico(),stringify(Map.of("nome","Corte simples","preco",new java.math.BigDecimal("25.90"),"duracaoMinutos",30,"ativo",false,"version",0)),a.conta().token());assertEquals(200,archived.statusCode(),archived.body());
        assertEquals(0,json(request("GET","/publico/"+a.link(),null,null)).get("servicos").size());
        assertEquals(404,reservar(a,pedido(a,"09:00")).statusCode());assertEquals(404,slots(a,a.servico(),a.profissional(),a.dia()).statusCode());
        assertEquals(0,json(request("GET","/clientes",null,a.conta().token())).size());
    }
    @Test void profissionaisDiferentesPodemReservarSimultaneamente() throws Exception {
        var p=publico("Agendas independentes");long outro=profissionalPublico(p.conta(),p.dia(),"Outro profissional");
        var a=pedido(p,"10:00");var b=pedido(p,"10:00");b.put("profissionalId",outro);
        var responses=concorrentes(()->reservar(p,a),()->reservar(p,b));
        assertEquals(List.of(201,201),responses.stream().map(HttpResponse::statusCode).sorted().toList());
        assertEquals(2,json(request("GET","/marcacoes",null,p.conta().token())).size());
    }
    @Test void reservasPublicasConcorrentesUmaVenceERepeticaoNaoDuplica() throws Exception {
        var p=publico("Concorrencia publica");var a=pedido(p,"10:00");var b=pedido(p,"10:00");
        var responses=concorrentes(()->reservar(p,a),()->reservar(p,b));
        assertEquals(List.of(201,409),responses.stream().map(HttpResponse::statusCode).sorted().toList());
        assertEquals(1,json(request("GET","/clientes",null,p.conta().token())).size());
        assertEquals(1,json(request("GET","/marcacoes",null,p.conta().token())).size());
        var same=pedido(p,"11:00");var retries=concorrentes(()->reservar(p,same),()->reservar(p,same));
        assertEquals(List.of(201,201),retries.stream().map(HttpResponse::statusCode).sorted().toList());
        assertEquals(json(retries.get(0)),json(retries.get(1)));
        assertEquals(2,json(request("GET","/clientes",null,p.conta().token())).size());
        assertEquals(2,json(request("GET","/marcacoes",null,p.conta().token())).size());
    }
    @Test void publicoNaoContornaAssinaturaExpirada() throws Exception {
        var p=publico("Assinatura expirada");
        jdbc.update("UPDATE estudios SET criado_em=?,pago_ate=NULL WHERE id=?",java.sql.Timestamp.valueOf(LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).minusDays(40)),p.conta().estudio());
        assertEquals(409,reservar(p,pedido(p,"10:00")).statusCode());
        assertEquals(0,json(request("GET","/clientes",null,p.conta().token())).size());
        assertEquals(0,json(request("GET","/marcacoes",null,p.conta().token())).size());
    }
    @Test void catalogoELivroCaixaExigemPermissaoProprietario() throws Exception {
        var p=publico("Permissoes");String equipe=funcionario(p.conta());
        assertEquals(200,request("GET","/servicos",null,equipe).statusCode());
        assertEquals(403,request("GET","/servicos/link",null,equipe).statusCode());
        var novo=stringify(Map.of("nome","Novo","preco",10,"duracaoMinutos",30,"ativo",true));
        assertEquals(403,request("POST","/servicos",novo,equipe).statusCode());
        assertEquals(403,request("PUT","/servicos/"+p.servico(),novo,equipe).statusCode());
        assertEquals(403,request("GET","/recebimentos",null,equipe).statusCode());
        long m=marcacaoParaReceber(p.conta(),"10:00","AGENDADA").get("id").asLong();
        assertEquals(403,request("POST","/recebimentos/marcacoes/"+m,"{\"valor\":10}",equipe).statusCode());
        for(var path:List.of("/servicos","/servicos/link","/recebimentos"))assertEquals(401,request("GET",path,null,null).statusCode());
        assertEquals(401,request("POST","/recebimentos/marcacoes/"+m,"{\"valor\":10}",null).statusCode());
        assertEquals(200,receber(p.conta(),m,"10.00").statusCode());
        var outra=conta("Outra conta");assertEquals(404,receber(outra,m,"10.00").statusCode());
        assertEquals(0,resumo(outra,null).get("recebimentos").size());
    }
    @Test void recebimentoSoPodeSerConfirmadoAposFimDaDuracaoSnapshot() throws Exception {
        var owner=conta("Recebimento apos fim");var body=reserva(cliente(owner),owner.profissional(),"10:00");body.put("duracaoMinutos",30);
        var created=request("POST","/marcacoes",stringify(body),owner.token());assertEquals(201,created.statusCode(),created.body());long id=json(created).get("id").asLong();
        var future=json(request("GET","/marcacoes/"+id,null,owner.token()));assertEquals(30,future.get("duracaoMinutos").asInt());assertFalse(future.get("recebimentoConfirmado").asBoolean());assertFalse(future.get("podeConfirmarRecebimento").asBoolean());
        assertEquals(409,receber(owner,id,"29.90").statusCode());assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM recebimentos WHERE marcacao_id=?",Integer.class,id));
        jdbc.update("UPDATE marcacoes SET data=? WHERE id=?",java.sql.Date.valueOf(LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusDays(1)),id);
        var elapsed=json(request("GET","/marcacoes/"+id,null,owner.token()));assertFalse(elapsed.get("recebimentoConfirmado").asBoolean());assertTrue(elapsed.get("podeConfirmarRecebimento").asBoolean());
        var paid=receber(owner,id,"29.90");assertEquals(200,paid.statusCode(),paid.body());dinheiro("29.90",json(paid).get("valor"));
        var completed=json(request("GET","/marcacoes/"+id,null,owner.token()));assertEquals("CONCLUIDA",completed.get("status").asText());assertEquals("COMPARECEU",completed.get("presenca").asText());assertTrue(completed.get("recebimentoConfirmado").asBoolean());assertFalse(completed.get("podeConfirmarRecebimento").asBoolean());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM recebimentos WHERE marcacao_id=?",Integer.class,id));var storedAmount=jdbc.queryForObject("SELECT valor FROM recebimentos WHERE marcacao_id=?",java.math.BigDecimal.class,id);assertNotNull(storedAmount);assertEquals(0,new java.math.BigDecimal("29.90").compareTo(storedAmount));
    }
    @Test void recebimentoIdempotenteConcluiEImpedeEditarOuExcluir() throws Exception {
        var owner=conta("Recebimento");var m=marcacaoParaReceber(owner,"10:00","AGENDADA");long id=m.get("id").asLong();
        assertFalse(m.get("recebimentoConfirmado").asBoolean());assertTrue(m.get("podeConfirmarRecebimento").asBoolean());
        var paid=receber(owner,id,"19.90");assertEquals(200,paid.statusCode(),paid.body());dinheiro("19.90",json(paid).get("valor"));
        assertNotNull(OffsetDateTime.parse(json(paid).get("confirmadoEm").asText()));
        var retry=receber(owner,id,"19.9");assertEquals(200,retry.statusCode(),retry.body());assertEquals(json(paid),json(retry));
        assertEquals(409,receber(owner,id,"20.00").statusCode());
        var saved=json(request("GET","/marcacoes/"+id,null,owner.token()));assertEquals("CONCLUIDA",saved.get("status").asText());
        assertTrue(saved.get("recebimentoConfirmado").asBoolean());assertFalse(saved.get("podeConfirmarRecebimento").asBoolean());
        var agenda=json(request("GET","/marcacoes",null,owner.token()));assertEquals(1,agenda.size());
        assertTrue(agenda.get(0).get("recebimentoConfirmado").asBoolean());assertFalse(agenda.get(0).get("podeConfirmarRecebimento").asBoolean());
        var edit=reserva(saved.get("cliente").get("id").asLong(),owner.profissional(),saved.get("horario").asText());
        edit.put("data",saved.get("data").asText());edit.put("status","CONCLUIDA");edit.put("version",saved.get("version").asLong());
        assertEquals(409,request("PUT","/marcacoes/"+id,stringify(edit),owner.token()).statusCode());
        edit.put("cliente",Map.of("nome","Outro","telefone","21999997777"));
        assertEquals(409,request("PUT","/marcacoes/"+id+"/com-cliente",stringify(edit),owner.token()).statusCode());
        assertEquals(409,request("DELETE","/marcacoes/"+id,null,owner.token()).statusCode());
        var sum=resumo(owner,null);assertEquals(1,sum.get("recebimentos").size());dinheiro("19.90",sum.get("totalMes"));dinheiro("19.90",sum.get("totalSemana"));
    }
    @Test void futurasCanceladasESemRecebimentoNaoContam() throws Exception {
        var owner=conta("Somente confirmados");
        var future=request("POST","/marcacoes",stringify(reserva(cliente(owner),owner.profissional(),"10:00")),owner.token());assertEquals(201,future.statusCode(),future.body());
        long futureId=json(future).get("id").asLong();
        var futureSaved=json(request("GET","/marcacoes/"+futureId,null,owner.token()));
        assertFalse(futureSaved.get("recebimentoConfirmado").asBoolean());assertFalse(futureSaved.get("podeConfirmarRecebimento").asBoolean());
        var futureList=json(request("GET","/marcacoes",null,owner.token()));assertEquals(1,futureList.size());
        assertFalse(futureList.get(0).get("recebimentoConfirmado").asBoolean());assertFalse(futureList.get(0).get("podeConfirmarRecebimento").asBoolean());
        assertEquals(409,receber(owner,futureId,"30.00").statusCode());
        var cancelledSaved=marcacaoParaReceber(owner,"11:00","CANCELADA");long cancelled=cancelledSaved.get("id").asLong();
        assertFalse(cancelledSaved.get("recebimentoConfirmado").asBoolean());assertFalse(cancelledSaved.get("podeConfirmarRecebimento").asBoolean());
        assertEquals(409,receber(owner,cancelled,"30.00").statusCode());
        marcacaoParaReceber(owner,"12:00","CONCLUIDA");marcacaoParaReceber(owner,"13:00","AGENDADA");
        var sum=resumo(owner,null);assertEquals(0,sum.get("recebimentos").size());dinheiro("0.00",sum.get("totalSemana"));dinheiro("0.00",sum.get("totalMes"));
    }
    @Test void recebimentosConcorrentesIdempotentesESemDuplicacao() throws Exception {
        var owner=conta("Concorrencia recebimentos");long id=marcacaoParaReceber(owner,"10:00","AGENDADA").get("id").asLong();
        var iguais=concorrentes(()->receber(owner,id,"0.10"),()->receber(owner,id,"0.10"));
        assertEquals(List.of(200,200),iguais.stream().map(HttpResponse::statusCode).sorted().toList());assertEquals(json(iguais.get(0)),json(iguais.get(1)));
        long outro=marcacaoParaReceber(owner,"11:00","AGENDADA").get("id").asLong();
        var diferentes=concorrentes(()->receber(owner,outro,"0.20"),()->receber(owner,outro,"0.30"));
        assertEquals(List.of(200,409),diferentes.stream().map(HttpResponse::statusCode).sorted().toList());
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM recebimentos WHERE estudio_id=?",Integer.class,owner.estudio()));
        var sum=resumo(owner,null);assertEquals(2,sum.get("recebimentos").size());
        var esperado=new java.math.BigDecimal("0.10").add(new java.math.BigDecimal(json(diferentes.stream().filter(r->r.statusCode()==200).findFirst().orElseThrow()).get("valor").asText()));
        dinheiro(esperado.toPlainString(),sum.get("totalMes"));
    }
    @Test void valoresInvalidosNaoPersistemEServicoExigeVersao() throws Exception {
        var owner=conta("Validacao monetaria");long id=marcacaoParaReceber(owner,"10:00","AGENDADA").get("id").asLong();
        for(var valor:List.of("-0.01","0.001","null"))assertEquals(400,receber(owner,id,valor).statusCode(),valor);
        assertEquals(0,resumo(owner,null).get("recebimentos").size());assertEquals(200,receber(owner,id,"0.00").statusCode());
        for(var valor:List.of("-0.01","0.001")){
            var response=request("POST","/servicos",stringify(Map.of("nome","Invalido","preco",new java.math.BigDecimal(valor),"duracaoMinutos",30,"ativo",true)),owner.token());assertEquals(400,response.statusCode(),response.body());
        }
        for(var minutos:List.of(0,721))assertEquals(400,request("POST","/servicos",stringify(Map.of("nome","Invalido","preco",10,"duracaoMinutos",minutos,"ativo",true)),owner.token()).statusCode());
        var s=servico(owner,"Valido","0.10",30);long sid=s.get("id").asLong();
        String body=stringify(Map.of("nome","Editado","preco",new java.math.BigDecimal("0.20"),"duracaoMinutos",30,"ativo",true,"version",s.get("version").asLong()));
        assertEquals(200,request("PUT","/servicos/"+sid,body,owner.token()).statusCode());assertEquals(409,request("PUT","/servicos/"+sid,body,owner.token()).statusCode());
    }
    @Test void resumoUsaBigDecimalConfirmacaoSemanaMesEFusoSemDuplicar() throws Exception {
        var owner=conta("Resumo financeiro");
        String[] instantes={"2026-01-01T02:59:59Z","2026-01-01T03:00:00Z","2026-01-04T02:59:59Z","2026-01-05T02:59:59Z","2026-02-01T02:59:59Z","2026-02-01T03:00:00Z","2025-12-29T02:59:59Z"};
        String[] valores={"0.10","0.20","0.30","0.40","0.50","0.60","0.70"};
        var incluidos=new HashSet<Long>();
        for(int i=0;i<instantes.length;i++){
            long mid=marcacaoParaReceber(owner,String.format(Locale.ROOT,"%02d:00",9+i),"AGENDADA").get("id").asLong();
            var paid=receber(owner,mid,valores[i]);assertEquals(200,paid.statusCode(),paid.body());long rid=json(paid).get("id").asLong();
            jdbc.update("UPDATE recebimentos SET confirmado_em=? WHERE id=?",OffsetDateTime.parse(instantes[i]),rid);
            if(i<5)incluidos.add(rid);
        }
        var sum=resumo(owner,"2026-01-01");assertEquals("BRL",sum.get("moeda").asText());assertEquals("America/Sao_Paulo",sum.get("fusoHorario").asText());
        assertEquals("2025-12-29",sum.get("inicioSemana").asText());assertEquals("2026-01-04",sum.get("fimSemana").asText());
        assertEquals("2026-01-01",sum.get("inicioMes").asText());assertEquals("2026-01-31",sum.get("fimMes").asText());
        dinheiro("1.00",sum.get("totalSemana"));dinheiro("1.40",sum.get("totalMes"));
        var ids=new HashSet<Long>();for(var row:sum.get("recebimentos")){
            assertTrue(ids.add(row.get("id").asLong()),"Recebimento duplicado na uniao semana/mes");
            assertEquals(ZoneOffset.ofHours(-3),OffsetDateTime.parse(row.get("confirmadoEm").asText()).getOffset());
        }
        assertEquals(incluidos,ids);
        assertEquals(400,request("GET","/recebimentos?data=nao-e-data",null,owner.token()).statusCode());
    }
    @Test void reservaPublicaValidaNomeTelefoneEIdsSemCriarRegistrosParciais() throws Exception {
        var p=publico("Validacao publica");
        assertEquals(400,request("POST","/publico/"+p.link()+"/reservas","{}",null).statusCode());
        for(var campo:List.of("nome","telefone","email")){
            var body=pedido(p,"10:00");body.put(campo,"");assertEquals(400,reservar(p,body).statusCode(),campo);
        }
        var telefone=pedido(p,"10:00");telefone.put("telefone","sem numero telefone");assertEquals(400,reservar(p,telefone).statusCode());
        var consentimento=pedido(p,"10:00");consentimento.put("avisoPrivacidadeLido",false);assertEquals(400,reservar(p,consentimento).statusCode());
        var chave=pedido(p,"10:00");chave.put("chave","invalida");assertEquals(400,reservar(p,chave).statusCode());
        for(var campo:List.of("servicoId","profissionalId")){
            var body=pedido(p,"10:00");body.put(campo,Long.MAX_VALUE);assertEquals(404,reservar(p,body).statusCode(),campo);
        }
        assertEquals(0,json(request("GET","/clientes",null,p.conta().token())).size());
        assertEquals(0,json(request("GET","/marcacoes",null,p.conta().token())).size());
    }
    @Test void agendaOrdenaServidorPorDataHorarioEId() throws Exception {
        var p=publico("Agenda ordenada");
        for(var hora:List.of("15:00","09:00","13:00")){
            var response=reservar(p,pedido(p,hora));assertEquals(201,response.statusCode(),response.body());
        }
        long outro=profissionalPublico(p.conta(),p.dia(),"Segunda agenda");
        var simultanea=pedido(p,"09:00");simultanea.put("profissionalId",outro);assertEquals(201,reservar(p,simultanea).statusCode());
        var agenda=json(request("GET","/marcacoes",null,p.conta().token()));assertEquals(4,agenda.size());
        var anterior=agenda.get(0);
        for(int i=1;i<agenda.size();i++){
            var atual=agenda.get(i);
            int ordemData=LocalDate.parse(anterior.get("data").asText()).compareTo(LocalDate.parse(atual.get("data").asText()));
            int ordemHora=LocalTime.parse(anterior.get("horario").asText()).compareTo(LocalTime.parse(atual.get("horario").asText()));
            assertTrue(ordemData<0 || (ordemData==0 && (ordemHora<0 || (ordemHora==0 && anterior.get("id").asLong()<atual.get("id").asLong()))),agenda.toString());
            anterior=atual;
        }
    }

    String configurar(Publico p,int antecedencia,int limite,String fuso)throws Exception{
        var body=Map.of("antecedenciaMinimaMinutos",antecedencia,"limiteAgendamentosPorEmail",limite,"fusoHorario",fuso);
        var response=request("PUT","/configuracao/agendamento",stringify(body),p.conta().token());
        assertEquals(200,response.statusCode(),response.body());return response.body();
    }
    long criarMarcacaoHistorica(Conta owner,LocalDate data,String horario,String status,String presenca,String servico,java.math.BigDecimal preco)throws Exception{
        long idCliente=cliente(owner);var body=reserva(idCliente,owner.profissional(),horario);body.put("descricao",servico);body.put("status","AGENDADA");
        var created=request("POST","/marcacoes",stringify(body),owner.token());assertEquals(201,created.statusCode(),created.body());
        long id=json(created).get("id").asLong();
        jdbc.update("UPDATE marcacoes SET data=?,status=?,presenca=?,servico_nome=?,preco_previsto=? WHERE id=?",
            java.sql.Date.valueOf(data),status,presenca,servico,preco,id);
        return id;
    }
    int criarReservaFuturaParaEmail(Publico p,String email,String hora)throws Exception{
        var body=pedido(p,hora);body.put("email",email);
        var created=reservar(p,body);assertEquals(201,created.statusCode(),created.body());return created.statusCode();
    }
    @Test void configuracaoAgendamentoValidaPermissaoTenantEValores() throws Exception {
        var p=publico("Configuração local");var b=conta("Outro estudio");var equipe=funcionario(p.conta());
        assertEquals(401,request("GET","/configuracao/agendamento",null,null).statusCode());
        assertEquals(403,request("GET","/configuracao/agendamento",null,equipe).statusCode());
        assertEquals(403,request("PUT","/configuracao/agendamento",stringify(Map.of("antecedenciaMinimaMinutos",120,"limiteAgendamentosPorEmail",3,"fusoHorario","UTC")),equipe).statusCode());
        var defaults=json(request("GET","/configuracao/agendamento",null,p.conta().token()));
        assertEquals(120,defaults.get("antecedenciaMinimaMinutos").asInt());assertEquals(3,defaults.get("limiteAgendamentosPorEmail").asInt());assertEquals("America/Sao_Paulo",defaults.get("fusoHorario").asText());
        for(var invalid:List.of(Map.of("antecedenciaMinimaMinutos",-1,"limiteAgendamentosPorEmail",3,"fusoHorario","UTC"),Map.of("antecedenciaMinimaMinutos",43201,"limiteAgendamentosPorEmail",3,"fusoHorario","UTC"),Map.of("antecedenciaMinimaMinutos",120,"limiteAgendamentosPorEmail",0,"fusoHorario","UTC"),Map.of("antecedenciaMinimaMinutos",120,"limiteAgendamentosPorEmail",51,"fusoHorario","Mars/Olympus")))
            assertEquals(400,request("PUT","/configuracao/agendamento",stringify(invalid),p.conta().token()).statusCode(),invalid.toString());
        var updated=request("PUT","/configuracao/agendamento",stringify(Map.of("antecedenciaMinimaMinutos",240,"limiteAgendamentosPorEmail",5,"fusoHorario","UTC")),p.conta().token());
        assertEquals(200,updated.statusCode(),updated.body());assertEquals(240,json(updated).get("antecedenciaMinimaMinutos").asInt());
        var bConfig=json(request("GET","/configuracao/agendamento",null,b.token()));assertEquals(120,bConfig.get("antecedenciaMinimaMinutos").asInt());assertEquals(3,bConfig.get("limiteAgendamentosPorEmail").asInt());assertEquals("America/Sao_Paulo",bConfig.get("fusoHorario").asText());
    }
    @Test void reservaExigeEmailAvisoEIsolaCadastroDoContatoDoCliente() throws Exception {
        var p=publico("Privacidade da reserva");int usuarios=jdbc.queryForObject("SELECT count(*) FROM usuarios",Integer.class);String email="Cliente.Aviso@example.com";
        for(var field:List.of("email","avisoPrivacidadeLido")){
            var missing=pedido(p,"09:00");missing.remove(field);assertEquals(400,reservar(p,missing).statusCode(),field);
        }
        var invalidEmail=pedido(p,"09:00");invalidEmail.put("email","nao-e-email");assertEquals(400,reservar(p,invalidEmail).statusCode());
        var unchecked=pedido(p,"09:00");unchecked.put("avisoPrivacidadeLido",false);assertEquals(400,reservar(p,unchecked).statusCode());
        assertEquals(0,json(request("GET","/clientes",null,p.conta().token())).size());
        assertEquals(usuarios,jdbc.queryForObject("SELECT count(*) FROM usuarios",Integer.class));
        var body=pedido(p,"09:00");body.put("email",email);var accepted=reservar(p,body);assertEquals(201,accepted.statusCode(),accepted.body());
        var contact=jdbc.queryForMap("SELECT email FROM cliente WHERE estudio_id=?",p.conta().estudio());assertEquals(email.toLowerCase(Locale.ROOT),((String)contact.get("email")).toLowerCase(Locale.ROOT));
        var booking=jdbc.queryForMap("SELECT aviso_privacidade_versao, aviso_privacidade_lido_em FROM marcacoes WHERE estudio_id=?",p.conta().estudio());
        assertEquals(json(request("GET","/publico/"+p.link(),null,null)).get("versaoAvisoPrivacidade").asText(),booking.get("aviso_privacidade_versao"));assertNotNull(booking.get("aviso_privacidade_lido_em"));
        assertFalse(accepted.body().contains(email));assertEquals(usuarios,jdbc.queryForObject("SELECT count(*) FROM usuarios",Integer.class));
    }
    @Test void antecedenciaMinimaSeAplicaNaDisponibilidadeENaReserva() throws Exception {
        var p=publico("Antecedencia");var now=ZonedDateTime.now(ZoneId.of("America/Sao_Paulo"));
        var turnos=new ArrayList<Map<String,Object>>();for(int day=1;day<=7;day++)turnos.add(Map.of("diaSemana",day,"inicio","00:00","fim","23:59"));
        var prof=json(request("GET","/profissionais",null,p.conta().token())).get(0);
        var updated=request("PUT","/profissionais/"+p.profissional(),stringify(Map.of("nome","Dia todo","ativo",true,"horarios",turnos,"version",prof.get("version").asLong())),p.conta().token());assertEquals(200,updated.statusCode(),updated.body());
        assertEquals(200,request("PUT","/configuracao/agendamento",stringify(Map.of("antecedenciaMinimaMinutos",120,"limiteAgendamentosPorEmail",3,"fusoHorario","America/Sao_Paulo")),p.conta().token()).statusCode());
        var limiteMinimo=now.plusMinutes(120).toLocalDateTime();var slot=limiteMinimo.withSecond(0).withNano(0);
        int remainder=slot.getMinute()%15;slot=slot.plusMinutes((15-remainder)%15);
        // A boundary can include seconds/nanos; if it is exactly on a slot, leave one full slot
        // of margin so the HTTP requests cannot consume the boundary before the server checks it.
        if(!slot.isAfter(limiteMinimo))slot=slot.plusMinutes(15);
        if(!slot.toLocalDate().equals(now.toLocalDate())||slot.toLocalTime().isBefore(LocalTime.of(1,0))||slot.toLocalTime().isAfter(LocalTime.of(23,0)))slot=now.toLocalDate().plusDays(1).atTime(2,0);
        assertFalse(slot.isBefore(limiteMinimo),"slot permitido deve ser >= limite exato com segundos/nanos");
        var candidate=new Publico(p.conta(),p.link(),p.servico(),p.profissional(),slot.toLocalDate());
        var limiteRecusa=now.plusMinutes(105).withSecond(0).withNano(0);
        var tooSoon=pedido(candidate,limiteRecusa.toLocalTime().toString());tooSoon.put("data",limiteRecusa.toLocalDate().toString());
        var refused=reservar(candidate,tooSoon);assertEquals(409,refused.statusCode(),refused.body());assertTrue(refused.body().contains("antecedencia"));
        var available=slots(p,p.servico(),p.profissional(),slot.toLocalDate());assertEquals(200,available.statusCode(),available.body());
        var listed=json(available).get("horarios");assertTrue(contem(listed,slot.toLocalTime().toString()+":00"));
        var allowed=pedido(candidate,slot.toLocalTime().toString());allowed.put("data",slot.toLocalDate().toString());
        assertEquals(201,reservar(candidate,allowed).statusCode());
    }
    @Test void limiteDeReservasAbertasPorEmailEPorEstudio() throws Exception {
        var a=publico("Limite A");var b=publico("Limite B");
        assertEquals(200,request("PUT","/configuracao/agendamento",stringify(Map.of("antecedenciaMinimaMinutos",120,"limiteAgendamentosPorEmail",1,"fusoHorario","America/Sao_Paulo")),a.conta().token()).statusCode());
        String shared="repetido@example.com";
        assertEquals(201,criarReservaFuturaParaEmail(a,shared,"09:00"));
        var second=pedido(a,"10:00");second.put("email",shared);assertEquals(429,reservar(a,second).statusCode());
        var otherTenant=pedido(b,"09:00");otherTenant.put("email",shared);assertEquals(201,reservar(b,otherTenant).statusCode());
        var list=json(request("GET","/clientes",null,a.conta().token()));assertEquals(1,list.size());
    }
    @Test void registrarPresencaExigeProprietarioFimDaSessaoERespeitaRecebimentos() throws Exception {
        var a=conta("Presenca");var b=conta("Outro estudo");String staff=funcionario(a);
        long future=criarMarcacaoHistorica(a,LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(2),"10:00","AGENDADA",null,"Futuro",new java.math.BigDecimal("29.90"));
        assertEquals(403,request("PUT","/marcacoes/"+future+"/presenca","{\"presenca\":\"FALTOU\"}",staff).statusCode());
        assertEquals(401,request("PUT","/marcacoes/"+future+"/presenca","{\"presenca\":\"FALTOU\"}",null).statusCode());
        assertEquals(404,request("PUT","/marcacoes/"+future+"/presenca","{\"presenca\":\"FALTOU\"}",b.token()).statusCode());
        assertEquals(409,request("PUT","/marcacoes/"+future+"/presenca","{\"presenca\":\"COMPARECEU\"}",a.token()).statusCode());
        var late=request("PUT","/marcacoes/"+future+"/presenca","{\"presenca\":\"UNKNOWN\"}",a.token());assertEquals(400,late.statusCode());
        var cancelled=criarMarcacaoHistorica(a,LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusDays(2),"11:00","CANCELADA",null,"Cancelada",null);
        assertEquals(409,request("PUT","/marcacoes/"+cancelled+"/presenca","{\"presenca\":\"FALTOU\"}",a.token()).statusCode());
        long due=criarMarcacaoHistorica(a,LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusDays(2),"12:00","AGENDADA",null,"Consulta",null);
        var attended=request("PUT","/marcacoes/"+due+"/presenca","{\"presenca\":\"COMPARECEU\"}",a.token());assertEquals(200,attended.statusCode(),attended.body());assertEquals("COMPARECEU",json(attended).get("presenca").asText());
        var missed=request("PUT","/marcacoes/"+due+"/presenca","{\"presenca\":\"FALTOU\"}",a.token());assertEquals(200,missed.statusCode(),missed.body());assertEquals("FALTOU",json(missed).get("presenca").asText());
        var received=criarMarcacaoHistorica(a,LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusDays(2),"13:00","AGENDADA",null,"Recebida",null);
        assertEquals(200,receber(a,received,"29.90").statusCode());
        assertEquals(409,request("PUT","/marcacoes/"+received+"/presenca","{\"presenca\":\"FALTOU\"}",a.token()).statusCode());
        assertEquals("COMPARECEU",json(request("GET","/marcacoes/"+received,null,a.token())).get("presenca").asText());
        assertTrue(json(request("GET","/marcacoes/"+future,null,a.token())).get("presenca").isNull());
    }
    @Test void historicoDeFaltasAgrupaEmailSoDentroDoTenant() throws Exception {
        var a=conta("Historico A");var b=conta("Historico B");long customerA=cliente(a);String email="historico@example.com";
        jdbc.update("UPDATE cliente SET email=? WHERE id=?",email,customerA);
        long missA=criarMarcacaoHistorica(a,LocalDate.now().minusDays(2),"10:00","AGENDADA","FALTOU","Corte",null);long missAClient=jdbc.queryForObject("SELECT cliente_id FROM marcacoes WHERE id=?",Long.class,missA);jdbc.update("UPDATE cliente SET email=? WHERE id=?",email,missAClient);
        long customerA2=cliente(a);jdbc.update("UPDATE cliente SET email=? WHERE id=?",email,customerA2);
        long missA2=criarMarcacaoHistorica(a,LocalDate.now().minusDays(3),"11:00","AGENDADA","FALTOU","Barba",null);long missA2Client=jdbc.queryForObject("SELECT cliente_id FROM marcacoes WHERE id=?",Long.class,missA2);jdbc.update("UPDATE cliente SET email=? WHERE id=?",email,missA2Client);
        long customerB=cliente(b);jdbc.update("UPDATE cliente SET email=? WHERE id=?",email,customerB);
        criarMarcacaoHistorica(b,LocalDate.now().minusDays(4),"12:00","AGENDADA","FALTOU","Privado",null);
        assertEquals(401,request("GET","/clientes/"+customerA+"/historico-faltas",null,null).statusCode());
        var own=json(request("GET","/clientes/"+customerA+"/historico-faltas",null,a.token()));assertEquals(2,own.size());
        assertTrue(own.toString().contains("Corte"));assertTrue(own.toString().contains("Barba"));assertFalse(own.toString().contains("Privado"));
        assertEquals(404,request("GET","/clientes/"+customerA+"/historico-faltas",null,b.token()).statusCode());
        assertEquals(404,request("GET","/clientes/999999/historico-faltas",null,a.token()).statusCode());
    }
    @Test void relatorioMensalDefineDenominadorMesVazioComparacaoETenant() throws Exception {
        var a=conta("Relatorio A");var b=conta("Relatorio B");String staff=funcionario(a);
        var month=YearMonth.now(ZoneId.of("America/Sao_Paulo")).minusMonths(1);var prior=month.minusMonths(1);
        long came=criarMarcacaoHistorica(a,month.atDay(2),"09:00","CONCLUIDA",null,"Servico snapshot",new java.math.BigDecimal("40.00"));
        long miss=criarMarcacaoHistorica(a,month.atDay(3),"10:00","AGENDADA","FALTOU","Servico snapshot",new java.math.BigDecimal("29.90"));
        criarMarcacaoHistorica(a,month.atDay(4),"11:00","CANCELADA",null,"Cancelamento",new java.math.BigDecimal("50.00"));
        criarMarcacaoHistorica(a,month.atDay(5),"12:00","AGENDADA",null,"Sem classificacao",new java.math.BigDecimal("10.00"));
        criarMarcacaoHistorica(a,prior.atDay(2),"13:00","AGENDADA",null,"Base vazia",null);
        long paid=criarMarcacaoHistorica(a,month.atDay(6),"14:00","AGENDADA",null,"Receita confirmadoEm",null);
        assertEquals(200,receber(a,paid,"0.10").statusCode());long receiptId=jdbc.queryForObject("SELECT id FROM recebimentos WHERE marcacao_id=?",Long.class,paid);
        jdbc.update("UPDATE recebimentos SET confirmado_em=? WHERE id=?",OffsetDateTime.of(month.atDay(1),LocalTime.of(2,59,59),ZoneOffset.UTC),receiptId);
        var response=request("GET","/relatorios/mensal?mes="+month,null,a.token());assertEquals(200,response.statusCode(),response.body());var report=json(response);
        assertEquals(month.toString(),report.get("mes").asText());assertEquals("America/Sao_Paulo",report.get("fusoHorario").asText());assertTrue(report.get("comparacaoDisponivel").asBoolean());
        var current=report.get("atual");assertEquals(4,current.get("agendamentos").asLong());assertEquals(2,current.get("comparecimentos").asLong());assertEquals(1,current.get("faltas").asLong());assertEquals(1,current.get("cancelamentos").asLong());
        assertEquals(3,current.get("denominadorComparecimento").asLong());dinheiro("66.67",current.get("taxaComparecimento"));dinheiro("29.90",current.get("valorPotencialFaltas"));
        dinheiro("0.00",current.get("receitaRealizada"));
        var previous=report.get("mesAnterior");assertEquals(1,previous.get("agendamentos").asLong());assertEquals(0,previous.get("denominadorComparecimento").asLong());assertTrue(previous.get("taxaComparecimento").isNull());assertTrue(report.get("variacoes").get("pontosPercentuais").isNull());
        assertEquals(month.lengthOfMonth(),report.get("dias").size());assertEquals(month.atDay(1).toString(),report.get("dias").get(0).get("data").asText());
        assertEquals(3,report.get("servicos").size());assertEquals("Servico snapshot",report.get("servicos").get(0).get("nome").asText());
        assertEquals(403,request("GET","/relatorios/mensal?mes="+month,null,staff).statusCode());assertEquals(401,request("GET","/relatorios/mensal",null,null).statusCode());
        assertEquals(0,json(request("GET","/relatorios/mensal?mes="+month,null,b.token())).get("atual").get("agendamentos").asInt());
        assertEquals(400,request("GET","/relatorios/mensal?mes=invalido",null,a.token()).statusCode());
    }
    @Test void relatorioContaReceitaPelaDataConfirmadaNoFusoEComparaMesVazio() throws Exception {
        var owner=conta("Relatorio timezone");var month=YearMonth.now(ZoneId.of("America/Sao_Paulo")).minusMonths(1);
        for(var timestamp:List.of(OffsetDateTime.of(month.atDay(1),LocalTime.of(2,59,59),ZoneOffset.UTC),OffsetDateTime.of(month.atDay(1),LocalTime.of(3,0),ZoneOffset.UTC))){
            long id=criarMarcacaoHistorica(owner,month.atDay(2),"09:00","AGENDADA",null,"Faturamento",null);assertEquals(200,receber(owner,id,"19.90").statusCode());
            long receipt=jdbc.queryForObject("SELECT id FROM recebimentos WHERE marcacao_id=?",Long.class,id);jdbc.update("UPDATE recebimentos SET confirmado_em=? WHERE id=?",timestamp,receipt);
        }
        var report=json(request("GET","/relatorios/mensal?mes="+month,null,owner.token()));dinheiro("19.90",report.get("atual").get("receitaRealizada"));
        dinheiro("19.90",report.get("mesAnterior").get("receitaRealizada"));
        var emptyMonth=month.minusMonths(2);var empty=json(request("GET","/relatorios/mensal?mes="+emptyMonth,null,owner.token()));
        assertFalse(empty.get("comparacaoDisponivel").asBoolean());assertEquals(0,empty.get("atual").get("agendamentos").asInt());assertTrue(empty.get("atual").get("taxaComparecimento").isNull());assertTrue(empty.get("variacoes").get("agendamentos").isNull());
    }
    @Test void relatorioIncluiFuturoNoTotalMasNaoNoDenominador() throws Exception {
        var owner=conta("Relatorio futuro");var month=YearMonth.now(ZoneId.of("America/Sao_Paulo")).plusMonths(1);long id=criarMarcacaoHistorica(owner,month.atDay(2),"09:00","AGENDADA",null,"Futuro",new java.math.BigDecimal("29.90"));
        var report=json(request("GET","/relatorios/mensal?mes="+month,null,owner.token()));assertEquals(1,report.get("atual").get("agendamentos").asInt());assertEquals(0,report.get("atual").get("denominadorComparecimento").asInt());
        assertFalse(report.get("comparacaoDisponivel").asBoolean());
    }
    @Test void cadastroNovoIniciaAvaliacaoSemPagamentoOuChamadaAoGateway() throws Exception {
        String email=UUID.randomUUID()+"@example.com";var inicio=LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        var cadastro=request("POST","/auth/estudios",stringify(Map.of("nomeEstudio","Avaliacao sem pagamento","nome","Responsavel","email",email,"senha","SenhaTeste123")),null);
        assertEquals(201,cadastro.statusCode(),cadastro.body());assertFalse(json(cadastro).has("pagamento"));assertFalse(json(cadastro).has("metodoPagamento"));
        String token=login(email,"SenhaTeste123");var resumoResponse=request("GET","/assinatura",null,token);assertEquals(200,resumoResponse.statusCode(),resumoResponse.body());var resumo=json(resumoResponse);
        assertEquals("AVALIACAO",resumo.get("status").asText());dinheiro("29.90",resumo.get("mensalidade"));assertEquals("BRL",resumo.get("moeda").asText());
        LocalDateTime avaliacaoAte=LocalDateTime.parse(resumo.get("avaliacaoAte").asText());assertTrue(avaliacaoAte.isAfter(inicio.plusDays(14).minusMinutes(1)));assertTrue(avaliacaoAte.isBefore(LocalDateTime.now().plusDays(14).plusMinutes(1)));
        long estudio=jdbc.queryForObject("SELECT estudio_id FROM usuarios WHERE email=?",Long.class,email);
        LocalDateTime criadoEm=jdbc.queryForObject("SELECT criado_em FROM estudios WHERE id=?",(rs,row)->rs.getTimestamp(1).toLocalDateTime(),estudio);
        assertTrue(avaliacaoAte.isAfter(criadoEm.plusDays(14).minusMinutes(1)));assertTrue(avaliacaoAte.isBefore(criadoEm.plusDays(14).plusMinutes(1)));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM assinatura_checkouts",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM assinatura_pagamentos",Integer.class));verify(gateway).disponivel();verifyNoMoreInteractions(gateway);
    }
    @Test void listaEsperaEIdempotenteEIsoladaPorEstudio() throws Exception {
        var a=publico("Lista A");var b=publico("Lista B");
        var inicio=a.dia().atTime(10,0).atZone(ZoneId.of("America/Sao_Paulo")).toOffsetDateTime();
        var pedido=new HashMap<String,Object>();pedido.put("chaveIdempotencia",UUID.randomUUID());pedido.put("servicoId",a.servico());
        pedido.put("profissionalId",a.profissional());pedido.put("inicio",inicio.toString());pedido.put("fim",inicio.plusMinutes(30).toString());
        pedido.put("nome","Interessada");pedido.put("email","interessada@example.com");pedido.put("telefone","21999998888");pedido.put("avisoPrivacidadeLido",true);
        var primeira=request("POST","/publico/"+a.link()+"/lista-espera",stringify(pedido),null);
        assertEquals(200,primeira.statusCode(),primeira.body());assertTrue(json(primeira).get("recebida").asBoolean());
        var repetida=request("POST","/publico/"+a.link()+"/lista-espera",stringify(pedido),null);
        assertEquals(200,repetida.statusCode(),repetida.body());
        pedido.put("email","outro@example.com");
        assertEquals(409,request("POST","/publico/"+a.link()+"/lista-espera",stringify(pedido),null).statusCode());
        var dono=json(request("GET","/lista-espera",null,a.conta().token()));assertEquals(1,dono.size());
        assertEquals("AGUARDANDO",dono.get(0).get("estado").asText());assertEquals("Interessada",dono.get(0).get("nome").asText());
        long id=dono.get(0).get("id").asLong();
        var aviso=jdbc.queryForMap("SELECT aviso_privacidade_versao,aviso_privacidade_lido_em FROM lista_espera WHERE id=? AND estudio_id=?",id,a.conta().estudio());
        assertEquals("2026-10-18-v1",aviso.get("aviso_privacidade_versao"));assertNotNull(aviso.get("aviso_privacidade_lido_em"));
        assertEquals(0,json(request("GET","/lista-espera",null,b.conta().token())).size());
        assertEquals(404,request("POST","/lista-espera/"+id+"/cancelar",null,b.conta().token()).statusCode());
        assertEquals(200,request("POST","/lista-espera/"+id+"/cancelar",null,a.conta().token()).statusCode());
        assertEquals("CANCELADA",json(request("GET","/lista-espera",null,a.conta().token())).get(0).get("estado").asText());
    }

    @Test void cancelarReservaCriaUmaOfertaEUmOutboxSemTokenBruto() throws Exception {
        var p=publico("Oferta transacional");var inicio=p.dia().atTime(9,0).atZone(ZoneId.of("America/Sao_Paulo")).toOffsetDateTime();
        var wait=new HashMap<String,Object>();wait.put("chaveIdempotencia",UUID.randomUUID());wait.put("servicoId",p.servico());wait.put("profissionalId",p.profissional());
        wait.put("inicio",inicio.toString());wait.put("fim",inicio.plusMinutes(30).toString());wait.put("nome","Fila");wait.put("email","fila@example.com");wait.put("telefone","21999998888");wait.put("avisoPrivacidadeLido",true);
        var joined=request("POST","/publico/"+p.link()+"/lista-espera",stringify(wait),null);assertEquals(200,joined.statusCode(),joined.body());
        var booked=reservar(p,pedido(p,"09:00"));assertEquals(201,booked.statusCode(),booked.body());
        var agenda=json(request("GET","/marcacoes",null,p.conta().token()));
        var saved=java.util.stream.StreamSupport.stream(agenda.spliterator(),false).filter(m->m.get("data").asText().equals(p.dia().toString())&&m.get("horario").asText().startsWith("09:00")).findFirst().orElseThrow();
        long id=saved.get("id").asLong();var cancel=new HashMap<String,Object>();cancel.put("cliente",Map.of("id",saved.get("cliente").get("id").asLong()));cancel.put("data",saved.get("data").asText());
        cancel.put("horario",saved.get("horario").asText());cancel.put("descricao",saved.get("descricao").asText());cancel.put("status","CANCELADA");
        cancel.put("duracaoMinutos",saved.get("duracaoMinutos").asInt());cancel.put("version",saved.get("version").asLong());cancel.put("profissionalId",p.profissional());
        var cancelled=request("PUT","/marcacoes/"+id,stringify(cancel),p.conta().token());assertEquals(200,cancelled.statusCode(),cancelled.body());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM ofertas_lista_espera WHERE estudio_id=?",Integer.class,p.conta().estudio()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM email_outbox WHERE estudio_id=? AND tipo='LISTA_ESPERA_OFERTA'",Integer.class,p.conta().estudio()));
        var outbox=jdbc.queryForMap("SELECT estado,dados_json FROM email_outbox WHERE estudio_id=? AND tipo='LISTA_ESPERA_OFERTA'",p.conta().estudio());
        assertEquals("PENDENTE",outbox.get("estado"));assertFalse(((String)outbox.get("dados_json")).toLowerCase(Locale.ROOT).contains("token"));
        var other=publico("Oferta outro tenant");byte[] raw=new byte[32];new java.security.SecureRandom().nextBytes(raw);String token=HexFormat.of().formatHex(raw);
        String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        long offerId=jdbc.queryForObject("SELECT id FROM ofertas_lista_espera WHERE estudio_id=?",Long.class,p.conta().estudio());
        jdbc.update("UPDATE ofertas_lista_espera SET estado='OFERTADA',token_hash=?,token_prazo=? WHERE id=?",hash,OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1),offerId);
        String tokenBody=stringify(Map.of("token",token));
        var expired=request("POST","/publico/"+p.link()+"/oferta/visualizar",tokenBody,null);assertEquals(410,expired.statusCode(),expired.body());
        assertFalse(expired.body().contains("fila@example.com"));
        assertEquals(404,request("POST","/publico/"+other.link()+"/oferta/visualizar",tokenBody,null).statusCode());
        jdbc.update("UPDATE ofertas_lista_espera SET token_prazo=? WHERE id=?",OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10),offerId);
        var preview=request("POST","/publico/"+p.link()+"/oferta/visualizar",tokenBody,null);assertEquals(200,preview.statusCode(),preview.body());
        assertEquals("Corte simples",json(preview).get("servico").asText());assertEquals(30,json(preview).get("duracaoMinutos").asInt());
        assertEquals(8,json(preview).size());for(var privateField:List.of("nome","email","telefone","cliente","clienteId","marcacaoId"))assertFalse(json(preview).has(privateField),privateField);
        var competitorBody=reserva(cliente(p.conta()),p.profissional(),"09:00");competitorBody.put("data",p.dia().toString());competitorBody.put("duracaoMinutos",30);
        var competitor=request("POST","/marcacoes",stringify(competitorBody),p.conta().token());assertEquals(201,competitor.statusCode(),competitor.body());
        assertEquals(409,request("POST","/publico/"+p.link()+"/oferta/aceitar",tokenBody,null).statusCode());
        var rival=json(competitor);var rivalCancel=new HashMap<String,Object>();rivalCancel.put("cliente",Map.of("id",rival.get("cliente").get("id").asLong()));
        rivalCancel.put("data",rival.get("data").asText());rivalCancel.put("horario",rival.get("horario").asText());rivalCancel.put("descricao",rival.get("descricao").asText());
        rivalCancel.put("status","CANCELADA");rivalCancel.put("duracaoMinutos",rival.get("duracaoMinutos").asInt());rivalCancel.put("version",rival.get("version").asLong());rivalCancel.put("profissionalId",p.profissional());
        var freed=request("PUT","/marcacoes/"+rival.get("id").asLong(),stringify(rivalCancel),p.conta().token());assertEquals(200,freed.statusCode(),freed.body());
        var accepted=concorrentes(()->request("POST","/publico/"+p.link()+"/oferta/aceitar",tokenBody,null),()->request("POST","/publico/"+p.link()+"/oferta/aceitar",tokenBody,null));
        assertEquals(List.of(200,200),accepted.stream().map(HttpResponse::statusCode).sorted().toList());
        long firstId=json(accepted.get(0)).get("reservaId").asLong();assertEquals(firstId,json(accepted.get(1)).get("reservaId").asLong());
        var replay=request("POST","/publico/"+p.link()+"/oferta/aceitar",tokenBody,null);assertEquals(200,replay.statusCode(),replay.body());assertEquals(firstId,json(replay).get("reservaId").asLong());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM marcacoes WHERE estudio_id=? AND data=? AND horario='09:00' AND status<>'CANCELADA'",Integer.class,p.conta().estudio(),java.sql.Date.valueOf(p.dia())));
        var reservedEntry=java.util.stream.StreamSupport.stream(json(request("GET","/lista-espera",null,p.conta().token())).spliterator(),false).filter(entry->entry.get("email").asText().equals("fila@example.com")).findFirst().orElseThrow();
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM email_outbox WHERE estudio_id=? AND referencia=? AND tipo IN ('LEMBRETE_24H','LEMBRETE_2H') AND estado='INVALIDADO'",Integer.class,p.conta().estudio(),Long.toString(id)));

        var secondWait=new HashMap<String,Object>();secondWait.put("chaveIdempotencia",UUID.randomUUID());secondWait.put("servicoId",p.servico());secondWait.put("profissionalId",p.profissional());
        var secondStart=p.dia().atTime(10,0).atZone(ZoneId.of("America/Sao_Paulo")).toOffsetDateTime();secondWait.put("inicio",secondStart.toString());secondWait.put("fim",secondStart.plusMinutes(30).toString());
        secondWait.put("nome","Fila cancelada");secondWait.put("email","fila-cancelada@example.com");secondWait.put("telefone","21999998888");secondWait.put("avisoPrivacidadeLido",true);
        assertEquals(200,request("POST","/publico/"+p.link()+"/lista-espera",stringify(secondWait),null).statusCode());
        var secondBooking=reservar(p,pedido(p,"10:00"));assertEquals(201,secondBooking.statusCode(),secondBooking.body());
        var secondAgenda=json(request("GET","/marcacoes",null,p.conta().token()));
        var secondSaved=java.util.stream.StreamSupport.stream(secondAgenda.spliterator(),false).filter(m->m.get("data").asText().equals(p.dia().toString())&&m.get("horario").asText().startsWith("10:00")&&m.get("status").asText().equals("AGENDADA")).findFirst().orElseThrow();
        long secondId=secondSaved.get("id").asLong();var secondCancel=new HashMap<String,Object>();secondCancel.put("cliente",Map.of("id",secondSaved.get("cliente").get("id").asLong()));
        secondCancel.put("data",secondSaved.get("data").asText());secondCancel.put("horario",secondSaved.get("horario").asText());secondCancel.put("descricao",secondSaved.get("descricao").asText());
        secondCancel.put("status","CANCELADA");secondCancel.put("duracaoMinutos",secondSaved.get("duracaoMinutos").asInt());secondCancel.put("version",secondSaved.get("version").asLong());secondCancel.put("profissionalId",p.profissional());
        assertEquals(200,request("PUT","/marcacoes/"+secondId,stringify(secondCancel),p.conta().token()).statusCode());
        var secondEntry=java.util.stream.StreamSupport.stream(json(request("GET","/lista-espera",null,p.conta().token())).spliterator(),false).filter(e->e.get("email").asText().equals("fila-cancelada@example.com")).findFirst().orElseThrow();
        assertEquals("OFERTADA",secondEntry.get("estado").asText());long secondEntryId=secondEntry.get("id").asLong();
        assertEquals(200,request("POST","/lista-espera/"+secondEntryId+"/cancelar",null,p.conta().token()).statusCode());
        long invalidatedOffer=jdbc.queryForObject("SELECT id FROM ofertas_lista_espera WHERE estudio_id=? AND lista_espera_id=?",Long.class,p.conta().estudio(),secondEntryId);
        assertEquals("INVALIDADA",jdbc.queryForObject("SELECT estado FROM ofertas_lista_espera WHERE id=?",String.class,invalidatedOffer));
        assertEquals(409,request("POST","/lista-espera/"+reservedEntry.get("id").asLong()+"/cancelar",null,p.conta().token()).statusCode());
        byte[] staleRaw=new byte[32];new java.security.SecureRandom().nextBytes(staleRaw);String staleToken=HexFormat.of().formatHex(staleRaw);
        String staleHash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(staleToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        jdbc.update("UPDATE ofertas_lista_espera SET token_hash=?,token_prazo=? WHERE id=?",staleHash,OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10),invalidatedOffer);
        var stale=request("POST","/publico/"+p.link()+"/oferta/visualizar",stringify(Map.of("token",staleToken)),null);
        assertEquals(410,stale.statusCode(),stale.body());assertFalse(stale.body().contains("fila-cancelada@example.com"));

        var boundaryWait=new HashMap<String,Object>();boundaryWait.put("chaveIdempotencia",UUID.randomUUID());boundaryWait.put("servicoId",p.servico());boundaryWait.put("profissionalId",p.profissional());
        var boundaryStart=p.dia().atTime(11,0).atZone(ZoneId.of("America/Sao_Paulo")).toOffsetDateTime();boundaryWait.put("inicio",boundaryStart.toString());boundaryWait.put("fim",boundaryStart.plusMinutes(30).toString());
        boundaryWait.put("nome","Limite superior");boundaryWait.put("email","limite@example.com");boundaryWait.put("telefone","21999998888");boundaryWait.put("avisoPrivacidadeLido",true);
        assertEquals(200,request("POST","/publico/"+p.link()+"/lista-espera",stringify(boundaryWait),null).statusCode());
        var boundaryBooking=reservar(p,pedido(p,"11:30"));assertEquals(201,boundaryBooking.statusCode(),boundaryBooking.body());
        var boundaryAgenda=json(request("GET","/marcacoes",null,p.conta().token()));
        var boundarySaved=java.util.stream.StreamSupport.stream(boundaryAgenda.spliterator(),false).filter(m->m.get("data").asText().equals(p.dia().toString())&&m.get("horario").asText().startsWith("11:30")).findFirst().orElseThrow();
        long boundaryId=boundarySaved.get("id").asLong();var boundaryCancel=new HashMap<String,Object>();boundaryCancel.put("cliente",Map.of("id",boundarySaved.get("cliente").get("id").asLong()));
        boundaryCancel.put("data",boundarySaved.get("data").asText());boundaryCancel.put("horario",boundarySaved.get("horario").asText());boundaryCancel.put("descricao",boundarySaved.get("descricao").asText());
        boundaryCancel.put("status","CANCELADA");boundaryCancel.put("duracaoMinutos",boundarySaved.get("duracaoMinutos").asInt());boundaryCancel.put("version",boundarySaved.get("version").asLong());boundaryCancel.put("profissionalId",p.profissional());
        assertEquals(200,request("PUT","/marcacoes/"+boundaryId,stringify(boundaryCancel),p.conta().token()).statusCode());
        var boundaryEntry=java.util.stream.StreamSupport.stream(json(request("GET","/lista-espera",null,p.conta().token())).spliterator(),false).filter(e->e.get("email").asText().equals("limite@example.com")).findFirst().orElseThrow();
        assertEquals("AGUARDANDO",boundaryEntry.get("estado").asText());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM ofertas_lista_espera WHERE estudio_id=? AND lista_espera_id=?",Integer.class,p.conta().estudio(),boundaryEntry.get("id").asLong()));
    }

    @Test void listaEsperaExigeQueDuracaoInteiraCaibaNaJanelaPedida() throws Exception {
        var p=publico("Janela integral da lista");var inicio=p.dia().atTime(9,0).atZone(ZoneId.of("America/Sao_Paulo")).toOffsetDateTime();
        var wait=new HashMap<String,Object>();wait.put("chaveIdempotencia",UUID.randomUUID());wait.put("servicoId",p.servico());wait.put("profissionalId",p.profissional());
        wait.put("inicio",inicio.toString());wait.put("fim",inicio.plusMinutes(15).toString());wait.put("nome","Janela curta");wait.put("email","janela-curta@example.com");wait.put("telefone","21999998888");wait.put("avisoPrivacidadeLido",true);
        assertEquals(200,request("POST","/publico/"+p.link()+"/lista-espera",stringify(wait),null).statusCode());
        var booked=reservar(p,pedido(p,"09:00"));assertEquals(201,booked.statusCode(),booked.body());
        var agenda=json(request("GET","/marcacoes",null,p.conta().token()));
        var saved=java.util.stream.StreamSupport.stream(agenda.spliterator(),false).filter(m->m.get("data").asText().equals(p.dia().toString())&&m.get("horario").asText().startsWith("09:00")).findFirst().orElseThrow();
        var cancel=new HashMap<String,Object>();cancel.put("cliente",Map.of("id",saved.get("cliente").get("id").asLong()));cancel.put("data",saved.get("data").asText());
        cancel.put("horario",saved.get("horario").asText());cancel.put("descricao",saved.get("descricao").asText());cancel.put("status","CANCELADA");
        cancel.put("duracaoMinutos",saved.get("duracaoMinutos").asInt());cancel.put("version",saved.get("version").asLong());cancel.put("profissionalId",p.profissional());
        var cancelled=request("PUT","/marcacoes/"+saved.get("id").asLong(),stringify(cancel),p.conta().token());assertEquals(200,cancelled.statusCode(),cancelled.body());
        var entry=java.util.stream.StreamSupport.stream(json(request("GET","/lista-espera",null,p.conta().token())).spliterator(),false).filter(e->e.get("email").asText().equals("janela-curta@example.com")).findFirst().orElseThrow();
        assertEquals("AGUARDANDO",entry.get("estado").asText());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM ofertas_lista_espera WHERE estudio_id=? AND lista_espera_id=?",Integer.class,p.conta().estudio(),entry.get("id").asLong()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM email_outbox WHERE estudio_id=? AND tipo='LISTA_ESPERA_OFERTA'",Integer.class,p.conta().estudio()));
    }

    @Test void encerramentoV17ExigePermissaoConfirmacaoEBloqueiaBillingAberto()throws Exception{
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM \"flyway_schema_history\" WHERE \"version\"='17' AND \"success\"=TRUE",Integer.class));
        assertDoesNotThrow(()->jdbc.queryForList("SELECT encerrado_em FROM estudios WHERE 1=0"));
        assertDoesNotThrow(()->jdbc.queryForList("SELECT operacional_ate,recebimentos_arquivo_concluido_em FROM expurgos_estudio WHERE 1=0"));
        assertDoesNotThrow(()->jdbc.queryForList("SELECT recebimento_origem_id,estudio_id,valor,confirmado_em,moeda,arquivado_em,ano_calendario,expira_em FROM recebimentos_arquivo WHERE 1=0"));
        assertDoesNotThrow(()->jdbc.queryForList("SELECT estudio_id,recebimento_origem_id,tipo,justificativa,escopo,revisar_em,revisada_em,encerrada_em FROM retencoes_legais_financeiras WHERE 1=0"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE LOWER(table_name)='expurgos_estudio' AND LOWER(column_name)='financeiro_ate'",Integer.class));
        assertEquals(Set.of("estudios"),tabelasReferenciadas("recebimentos_arquivo"));
        assertEquals(Set.of("estudios"),tabelasReferenciadas("retencoes_legais_financeiras"));

        var recorrente=conta("Encerramento recorrencia");String staff=funcionario(recorrente);
        assertEquals(401,request("POST","/estudio/encerramento","{\"confirmacao\":\"ENCERRAR\"}",null).statusCode());
        assertEquals(403,fechar(new Conta(staff,recorrente.profissional(),recorrente.estudio(),recorrente.usuario(),recorrente.email()),"ENCERRAR").statusCode());
        assertEquals(400,fechar(recorrente,"ENCERRAR AGORA").statusCode());
        inserirCheckout(recorrente,"CARTAO","authorized");
        assertEquals(409,fechar(recorrente,"ENCERRAR").statusCode());

        var periodoPago=conta("Encerramento periodo pago");
        jdbc.update("UPDATE estudios SET pago_ate=? WHERE id=?",java.sql.Timestamp.valueOf(LocalDateTime.now(clock).plusDays(1)),periodoPago.estudio());
        assertEquals(409,fechar(periodoPago,"ENCERRAR").statusCode());

        var checkoutPendente=conta("Encerramento checkout pendente");inserirCheckout(checkoutPendente,"PIX","pending");
        assertEquals(409,fechar(checkoutPendente,"ENCERRAR").statusCode());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM expurgos_estudio",Integer.class));
        assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM estudios WHERE encerrado_em IS NULL",Integer.class));
    }

    @Test void fechamentoAutenticadoRevogaAcessoTokensLinksEOutboxEGravaPrazos()throws Exception{
        var p=publico("Fechamento autenticacao e outbox");var owner=p.conta();String staff=funcionario(owner);
        cancelarReservaQueGeraOferta(p,"09:00","offer-target@example.test");
        long offerId=jdbc.queryForObject("SELECT id FROM ofertas_lista_espera WHERE estudio_id=?",Long.class,owner.estudio());
        String offerToken=inserirTokenOferta(owner.estudio(),offerId);
        long booking=marcacaoParaReceber(owner,"10:00","AGENDADA").get("id").asLong();
        assertEquals(200,receber(owner,booking,"19.90").statusCode());
        String actionToken=inserirTokenAcao(owner.estudio(),booking);
        jdbc.update("INSERT INTO recuperacoes_senha(token_hash,usuario_id,prazo) VALUES (?,?,?)","a".repeat(64),owner.usuario(),LocalDateTime.now(clock).plusDays(1));
        inserirOutbox(owner,"PENDENTE","pending");inserirOutbox(owner,"ENVIANDO","leased");
        String publicLink=p.link();

        assertEquals(403,fechar(new Conta(staff,owner.profissional(),owner.estudio(),owner.usuario(),owner.email()),"ENCERRAR").statusCode());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM expurgos_estudio WHERE estudio_id=?",Integer.class,owner.estudio()));
        assertEquals(409,fechar(owner,"ENCERRAR").statusCode());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM expurgos_estudio WHERE estudio_id=?",Integer.class,owner.estudio()));
        jdbc.update("UPDATE email_outbox SET lease_ate=? WHERE estudio_id=? AND chave_idempotencia=?",
            java.time.OffsetDateTime.ofInstant(clock.instant().minusSeconds(1),ZoneOffset.UTC),owner.estudio(),"purge-leased");
        var response=fechar(owner,"ENCERRAR");assertEquals(202,response.statusCode(),response.body());var result=json(response);
        assertEquals("ENCERRAMENTO_CONFIRMADO",result.get("estado").asText());assertTrue(result.get("arquivoFinanceiroAguardaPrazo").asBoolean());
        Instant closed=Instant.parse(result.get("encerradoEm").asText());
        assertSegundosEntre(closed,Instant.parse(result.get("expurgoListaEsperaEm").asText()),24*60*60,1);
        assertSegundosEntre(closed,Instant.parse(result.get("expurgoOperacionalEm").asText()),30*24*60*60,1);
        var job=jdbc.queryForMap("SELECT * FROM expurgos_estudio WHERE estudio_id=?",owner.estudio());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM expurgos_estudio WHERE estudio_id=?",Integer.class,owner.estudio()));
        assertNull(job.get("recebimentos_arquivo_concluido_em"));assertNotNull(jdbc.queryForObject("SELECT encerrado_em FROM estudios WHERE id=?",Object.class,owner.estudio()));
        assertNotEquals(publicLink,jdbc.queryForObject("SELECT link_publico FROM estudios WHERE id=?",String.class,owner.estudio()));
        var account=jdbc.queryForMap("SELECT ativo,token_version FROM usuarios WHERE id=?",owner.usuario());
        assertFalse((Boolean)account.get("ativo"));assertTrue(((Number)account.get("token_version")).longValue()>0);
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE estudio_id=? AND ativo=TRUE",Integer.class,owner.estudio()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM email_action_tokens WHERE estudio_id=?",Integer.class,owner.estudio()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM recuperacoes_senha WHERE usuario_id=?",Integer.class,owner.usuario()));
        for(String key:List.of("purge-pending","purge-leased")){
            var row=jdbc.queryForMap("SELECT estado,lease_ate,lease_token FROM email_outbox WHERE estudio_id=? AND chave_idempotencia=?",owner.estudio(),key);
            assertEquals("INVALIDADO",row.get("estado"));assertNull(row.get("lease_ate"));assertNull(row.get("lease_token"));
        }
        assertTrue(emailOutboxService.claimReady(clock.instant()).isEmpty());
        assertEquals(401,request("GET","/auth/me",null,owner.token()).statusCode());
        assertEquals(401,request("POST","/auth/login",stringify(Map.of("email",owner.email(),"senha","SenhaTeste123")),null).statusCode());
        assertEquals(404,request("GET","/publico/"+publicLink,null,null).statusCode());
        assertEquals(404,reservar(new Publico(owner,publicLink,p.servico(),p.profissional(),p.dia()),pedido(p,"11:00")).statusCode());
        var offerView=request("POST","/publico/"+publicLink+"/oferta/visualizar",stringify(Map.of("token",offerToken)),null);
        assertTrue(Set.of(404,410).contains(offerView.statusCode()),offerView.body());assertFalse(offerView.body().contains("offer-target@example.test"));
        assertEquals(410,request("POST","/publico/acoes/visualizar",stringify(Map.of("token",actionToken)),null).statusCode());
        assertEquals(401,fechar(owner,"ENCERRAR").statusCode());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM expurgos_estudio WHERE estudio_id=?",Integer.class,owner.estudio()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM recebimentos WHERE marcacao_id=?",Integer.class,booking));
    }

    @Test void expurgoWorkerDesligadoNaoApagaMesmoComPrazosVencidos()throws Exception{
        var owner=conta("Expurgo desligado");long booking=marcacaoParaReceber(owner,"10:00","AGENDADA").get("id").asLong();
        assertEquals(200,receber(owner,booking,"29.90").statusCode());assertEquals(202,fechar(owner,"ENCERRAR").statusCode());
        tornarFilaVencida(owner.estudio());tornarOperacionalVencido(owner.estudio());
        assertFalse((Boolean)org.springframework.test.util.ReflectionTestUtils.getField(expurgoEstudioWorker,"enabled"));
        expurgoEstudioWorker.poll();
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM marcacoes WHERE estudio_id=?",Integer.class,owner.estudio()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM recebimentos WHERE estudio_id=?",Integer.class,owner.estudio()));
        var job=jdbc.queryForMap("SELECT lista_espera_concluido_em,operacional_concluido_em FROM expurgos_estudio WHERE estudio_id=?",owner.estudio());
        assertNull(job.get("lista_espera_concluido_em"));assertNull(job.get("operacional_concluido_em"));
    }

    @Test void expurgoExecutaEtapasArquivaRecebimentoSemPiiEIsolaTenant()throws Exception{
        var a=publico("Expurgo alvo A");var b=publico("Expurgo preservado B");
        cancelarReservaQueGeraOferta(a,"09:00","fila-a@example.test");cancelarReservaQueGeraOferta(b,"09:00","fila-b@example.test");
        long bookingA=marcacaoParaReceber(a.conta(),"10:00","AGENDADA").get("id").asLong();
        long bookingB=marcacaoParaReceber(b.conta(),"10:00","AGENDADA").get("id").asLong();
        assertEquals(200,receber(a.conta(),bookingA,"19.90").statusCode());assertEquals(200,receber(b.conta(),bookingB,"7.25").statusCode());
        long receiptA=jdbc.queryForObject("SELECT id FROM recebimentos WHERE marcacao_id=?",Long.class,bookingA);
        Instant confirmedA=instanteBanco(jdbc.queryForObject("SELECT confirmado_em FROM recebimentos WHERE id=?",Object.class,receiptA));
        inserirLedger(a.conta(),"cancelled");inserirLedger(b.conta(),"cancelled");
        inserirOutbox(a.conta(),"ENVIANDO","target-lease");inserirOutbox(b.conta(),"PENDENTE","other-pending");

        var blockedByLease=fechar(a.conta(),"ENCERRAR");assertEquals(409,blockedByLease.statusCode(),blockedByLease.body());
        jdbc.update("UPDATE email_outbox SET lease_ate=? WHERE estudio_id=? AND estado='ENVIANDO'",
            java.time.OffsetDateTime.ofInstant(clock.instant().minusSeconds(1),ZoneOffset.UTC),a.conta().estudio());
        var closed=fechar(a.conta(),"ENCERRAR");assertEquals(202,closed.statusCode(),closed.body());
        assertTrue(jdbc.queryForObject("SELECT count(*) FROM email_outbox WHERE estudio_id=? AND estado='INVALIDADO'",Integer.class,a.conta().estudio())>=1);
        tornarFilaVencida(a.conta().estudio());worker().poll();
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM ofertas_lista_espera WHERE estudio_id=?",Integer.class,a.conta().estudio()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM lista_espera WHERE estudio_id=?",Integer.class,a.conta().estudio()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM email_outbox WHERE estudio_id=?",Integer.class,a.conta().estudio()));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM marcacoes WHERE estudio_id=?",Integer.class,a.conta().estudio()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM recebimentos WHERE estudio_id=?",Integer.class,a.conta().estudio()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM ofertas_lista_espera WHERE estudio_id=?",Integer.class,b.conta().estudio()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM lista_espera WHERE estudio_id=?",Integer.class,b.conta().estudio()));
        assertTrue(jdbc.queryForObject("SELECT count(*) FROM email_outbox WHERE estudio_id=? AND estado='PENDENTE'",Integer.class,b.conta().estudio())>=1);

        tornarOperacionalVencido(a.conta().estudio());worker().poll();
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM recebimentos WHERE estudio_id=?",Integer.class,a.conta().estudio()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM marcacoes WHERE estudio_id=?",Integer.class,a.conta().estudio()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM cliente WHERE estudio_id=?",Integer.class,a.conta().estudio()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE estudio_id=?",Integer.class,a.conta().estudio()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM recebimentos_arquivo WHERE estudio_id=?",Integer.class,a.conta().estudio()));
        var archive=jdbc.queryForMap("SELECT recebimento_origem_id AS \"source_id\",estudio_id AS \"studio_id\",valor AS \"amount\",confirmado_em AS \"confirmed_at\",moeda AS \"currency\",arquivado_em AS \"archived_at\",ano_calendario AS \"calendar_year\",expira_em AS \"expires_at\" FROM recebimentos_arquivo WHERE estudio_id=?",a.conta().estudio());
        assertEquals(Set.of("source_id","studio_id","amount","confirmed_at","currency","archived_at","calendar_year","expires_at"),archive.keySet());
        assertEquals(receiptA,((Number)archive.get("source_id")).longValue());assertEquals(a.conta().estudio(),((Number)archive.get("studio_id")).longValue());
        assertEquals(0,new java.math.BigDecimal("19.90").compareTo((java.math.BigDecimal)archive.get("amount")));
        int receiptYear=LocalDate.ofInstant(confirmedA,ZoneId.of("America/Sao_Paulo")).getYear();
        Instant expectedExpiry=LocalDate.of(receiptYear+6,1,1).atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant();
        assertEquals(confirmedA,instanteBanco(archive.get("confirmed_at")));assertEquals(receiptYear,((Number)archive.get("calendar_year")).intValue());
        assertEquals("BRL",archive.get("currency"));assertEquals(expectedExpiry,instanteBanco(archive.get("expires_at")));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM assinatura_pagamentos WHERE estudio_id=?",Integer.class,a.conta().estudio()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM assinatura_checkouts WHERE estudio_id=? AND checkout_url IS NULL",Integer.class,a.conta().estudio()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM recebimentos WHERE estudio_id=? AND marcacao_id=?",Integer.class,b.conta().estudio(),bookingB));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM recebimentos_arquivo WHERE estudio_id=?",Integer.class,b.conta().estudio()));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM marcacoes WHERE estudio_id=?",Integer.class,b.conta().estudio()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE estudio_id=? AND ativo=TRUE",Integer.class,b.conta().estudio()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM assinatura_pagamentos WHERE estudio_id=?",Integer.class,b.conta().estudio()));
    }

    @Test void falhaDeFkReverteEtapaInteiraAgendaRetryESucessoRepetidoNaoDuplicaArquivo()throws Exception{
        var owner=conta("Expurgo retry");long booking=marcacaoParaReceber(owner,"10:00","AGENDADA").get("id").asLong();
        assertEquals(200,receber(owner,booking,"31.40").statusCode());assertEquals(202,fechar(owner,"ENCERRAR").statusCode());
        tornarFilaVencida(owner.estudio());tornarOperacionalVencido(owner.estudio());
        jdbc.execute("CREATE TABLE expurgo_test_barrier (marcacao_id BIGINT PRIMARY KEY REFERENCES marcacoes(id))");
        jdbc.update("INSERT INTO expurgo_test_barrier(marcacao_id) VALUES (?)",booking);
        try{
            worker().poll();
            var failed=jdbc.queryForMap("SELECT tentativas,proxima_tentativa_em,ultimo_erro,lista_espera_concluido_em,operacional_concluido_em FROM expurgos_estudio WHERE estudio_id=?",owner.estudio());
            assertEquals(1,((Number)failed.get("tentativas")).intValue());assertSegundosEntre(clock.instant(),instanteBanco(failed.get("proxima_tentativa_em")),30,1);
            assertEquals("EXPURGO_RETRY",failed.get("ultimo_erro"));assertNull(failed.get("lista_espera_concluido_em"));assertNull(failed.get("operacional_concluido_em"));
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM recebimentos WHERE estudio_id=?",Integer.class,owner.estudio()));
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM recebimentos_arquivo WHERE estudio_id=?",Integer.class,owner.estudio()));
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM marcacoes WHERE estudio_id=?",Integer.class,owner.estudio()));
        }finally{
            jdbc.execute("DROP TABLE IF EXISTS expurgo_test_barrier");
        }
        jdbc.update("UPDATE expurgos_estudio SET proxima_tentativa_em=? WHERE estudio_id=?",
            java.time.OffsetDateTime.ofInstant(clock.instant().minusSeconds(1),ZoneOffset.UTC),owner.estudio());
        worker().poll();
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM recebimentos WHERE estudio_id=?",Integer.class,owner.estudio()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM recebimentos_arquivo WHERE estudio_id=?",Integer.class,owner.estudio()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM marcacoes WHERE estudio_id=?",Integer.class,owner.estudio()));
        worker().poll();
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM recebimentos_arquivo WHERE estudio_id=?",Integer.class,owner.estudio()));
        assertEquals(0,jdbc.queryForObject("SELECT tentativas FROM expurgos_estudio WHERE estudio_id=?",Integer.class,owner.estudio()));
    }

    @Test void retencaoFinanceiraPorRecebimentoRespeitaAnoCalendarioEFusoNaVirada()throws Exception{
        var owner=conta("Retencao por recebimento e virada de ano");
        long bookingBefore=marcacaoParaReceber(owner,"10:00","AGENDADA").get("id").asLong();
        long bookingAfter=marcacaoParaReceber(owner,"11:00","AGENDADA").get("id").asLong();
        assertEquals(200,receber(owner,bookingBefore,"42.00").statusCode());
        assertEquals(200,receber(owner,bookingAfter,"17.25").statusCode());
        long receiptBefore=jdbc.queryForObject("SELECT id FROM recebimentos WHERE marcacao_id=?",Long.class,bookingBefore);
        long receiptAfter=jdbc.queryForObject("SELECT id FROM recebimentos WHERE marcacao_id=?",Long.class,bookingAfter);
        // 02:59:59Z is still 31/12 in São Paulo; 03:00:00Z is exactly local midnight.
        Instant beforeLocalYearBoundary=Instant.parse("2022-01-01T02:59:59Z");
        Instant atLocalYearBoundary=Instant.parse("2022-01-01T03:00:00Z");
        jdbc.update("UPDATE recebimentos SET confirmado_em=? WHERE id=?",utc(beforeLocalYearBoundary),receiptBefore);
        jdbc.update("UPDATE recebimentos SET confirmado_em=? WHERE id=?",utc(atLocalYearBoundary),receiptAfter);
        assertEquals(2021,LocalDate.ofInstant(beforeLocalYearBoundary,ZoneId.of("America/Sao_Paulo")).getYear());
        assertEquals(2022,LocalDate.ofInstant(atLocalYearBoundary,ZoneId.of("America/Sao_Paulo")).getYear());
        inserirLedger(owner,"cancelled");
        assertEquals(202,fechar(owner,"ENCERRAR").statusCode());
        tornarFilaVencida(owner.estudio());tornarOperacionalVencido(owner.estudio());

        worker().poll();
        var before=jdbc.queryForMap("SELECT estudio_id,valor,confirmado_em,ano_calendario,expira_em FROM recebimentos_arquivo WHERE recebimento_origem_id=?",receiptBefore);
        var after=jdbc.queryForMap("SELECT estudio_id,valor,confirmado_em,ano_calendario,expira_em FROM recebimentos_arquivo WHERE recebimento_origem_id=?",receiptAfter);
        assertEquals(owner.estudio(),((Number)before.get("estudio_id")).longValue());
        assertEquals(2021,((Number)before.get("ano_calendario")).intValue());
        assertEquals(beforeLocalYearBoundary,instanteBanco(before.get("confirmado_em")));
        assertEquals(Instant.parse("2027-01-01T03:00:00Z"),instanteBanco(before.get("expira_em")));
        assertEquals(2022,((Number)after.get("ano_calendario")).intValue());
        assertEquals(atLocalYearBoundary,instanteBanco(after.get("confirmado_em")));
        assertEquals(Instant.parse("2028-01-01T03:00:00Z"),instanteBanco(after.get("expira_em")));
        assertTrue(instanteBanco(before.get("expira_em")).isAfter(clock.instant()));
        assertTrue(instanteBanco(after.get("expira_em")).isAfter(instanteBanco(before.get("expira_em"))));

        // The financial snapshot has no FK to the operational rows: those can be deleted first.
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM recebimentos WHERE estudio_id=?",Integer.class,owner.estudio()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM marcacoes WHERE estudio_id=?",Integer.class,owner.estudio()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM cliente WHERE estudio_id=?",Integer.class,owner.estudio()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE estudio_id=?",Integer.class,owner.estudio()));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM recebimentos_arquivo WHERE estudio_id=?",Integer.class,owner.estudio()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM assinatura_pagamentos WHERE estudio_id=?",Integer.class,owner.estudio()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM assinatura_checkouts WHERE estudio_id=?",Integer.class,owner.estudio()));
        worker().poll();
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM recebimentos_arquivo WHERE estudio_id=?",Integer.class,owner.estudio()));
    }

    @Test void retencoesLegaisPorRecebimentoBloqueiamAteEncerramentoExplicitoEIsolamTenant()throws Exception{
        var owner=conta("Holds financeiros");
        long activeBooking=marcacaoParaReceber(owner,"10:00","AGENDADA").get("id").asLong();
        long overdueBooking=marcacaoParaReceber(owner,"11:00","AGENDADA").get("id").asLong();
        long releasedBooking=marcacaoParaReceber(owner,"12:00","AGENDADA").get("id").asLong();
        long unmatchedBooking=marcacaoParaReceber(owner,"13:00","AGENDADA").get("id").asLong();
        assertEquals(200,receber(owner,activeBooking,"10.00").statusCode());
        assertEquals(200,receber(owner,overdueBooking,"20.00").statusCode());
        assertEquals(200,receber(owner,releasedBooking,"30.00").statusCode());
        assertEquals(200,receber(owner,unmatchedBooking,"40.00").statusCode());
        long activeReceipt=jdbc.queryForObject("SELECT id FROM recebimentos WHERE marcacao_id=?",Long.class,activeBooking);
        long overdueReceipt=jdbc.queryForObject("SELECT id FROM recebimentos WHERE marcacao_id=?",Long.class,overdueBooking);
        long releasedReceipt=jdbc.queryForObject("SELECT id FROM recebimentos WHERE marcacao_id=?",Long.class,releasedBooking);
        long unmatchedReceipt=jdbc.queryForObject("SELECT id FROM recebimentos WHERE marcacao_id=?",Long.class,unmatchedBooking);
        inserirLedger(owner,"cancelled");
        assertEquals(202,fechar(owner,"ENCERRAR").statusCode());
        Instant now=clock.instant(),started=now.minus(Duration.ofDays(2)),reviewExpired=now.minus(Duration.ofDays(1));
        Instant oldConfirmed=Instant.parse("2018-06-01T12:00:00Z");
        for(long receiptId:List.of(activeReceipt,overdueReceipt,releasedReceipt,unmatchedReceipt))
            jdbc.update("UPDATE recebimentos SET confirmado_em=? WHERE id=?",utc(oldConfirmed),receiptId);
        // Holds may exist before the minimized archive row; their receipt ID is scoped by studio in worker queries.
        inserirHold(owner.estudio(),activeReceipt,"OBRIGACAO_ESPECIFICA","Obrigação identificada","Somente este recebimento",started,now.plus(Duration.ofDays(30)),null,null);
        inserirHold(owner.estudio(),overdueReceipt,"PROCESSO_PENDENTE","Processo em andamento","Valor e data deste recebimento",started,reviewExpired,null,null);
        inserirHold(owner.estudio(),releasedReceipt,"FISCALIZACAO_PENDENTE","Fiscalização encerrada","Somente este recebimento",started,reviewExpired,now,now);

        var other=conta("Outro tenant para escopo do hold");
        inserirHold(other.estudio(),unmatchedReceipt,"OBRIGACAO_ESPECIFICA","Hold de outro estúdio","Não deve alcançar o recebimento",started,now.plus(Duration.ofDays(30)),null,null);
        tornarFilaVencida(owner.estudio());tornarOperacionalVencido(owner.estudio());

        worker().poll();
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM recebimentos_arquivo WHERE recebimento_origem_id=?",Integer.class,activeReceipt));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM recebimentos_arquivo WHERE recebimento_origem_id=?",Integer.class,overdueReceipt),
                "revisar_em vencido sem encerramento explícito ainda deve bloquear o expurgo");
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM recebimentos_arquivo WHERE recebimento_origem_id=?",Integer.class,releasedReceipt));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM recebimentos_arquivo WHERE recebimento_origem_id=?",Integer.class,unmatchedReceipt),
                "hold de outro tenant não pode bloquear o recebimento");
        var activeHold=jdbc.queryForMap("SELECT tipo,justificativa,escopo,revisar_em,revisada_em,encerrada_em FROM retencoes_legais_financeiras WHERE estudio_id=? AND recebimento_origem_id=?",owner.estudio(),activeReceipt);
        assertEquals("OBRIGACAO_ESPECIFICA",activeHold.get("tipo"));assertEquals("Obrigação identificada",activeHold.get("justificativa"));
        assertEquals("Somente este recebimento",activeHold.get("escopo"));assertNull(activeHold.get("revisada_em"));assertNull(activeHold.get("encerrada_em"));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM retencoes_legais_financeiras WHERE estudio_id=? AND recebimento_origem_id=? AND encerrada_em IS NULL",Integer.class,owner.estudio(),activeReceipt));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM retencoes_legais_financeiras WHERE estudio_id=? AND recebimento_origem_id=? AND revisar_em<? AND revisada_em IS NULL AND encerrada_em IS NULL",Integer.class,owner.estudio(),overdueReceipt,utc(now)));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM retencoes_legais_financeiras WHERE estudio_id=? AND recebimento_origem_id=?",Integer.class,owner.estudio(),releasedReceipt),
                "hold encerrado deve ser limpo antes de remover o arquivo referenciado");
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM retencoes_legais_financeiras WHERE estudio_id=? AND recebimento_origem_id=? AND encerrada_em IS NULL",Integer.class,other.estudio(),unmatchedReceipt));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM recebimentos WHERE estudio_id=?",Integer.class,owner.estudio()));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM marcacoes WHERE estudio_id=?",Integer.class,owner.estudio()));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM recebimentos_arquivo WHERE estudio_id=?",Integer.class,owner.estudio()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM assinatura_pagamentos WHERE estudio_id=?",Integer.class,owner.estudio()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM assinatura_checkouts WHERE estudio_id=?",Integer.class,owner.estudio()),
                "o ledger Stripe/assinatura está fora do expurgo de recebimentos");

        worker().poll();
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM recebimentos_arquivo WHERE estudio_id=?",Integer.class,owner.estudio()));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM retencoes_legais_financeiras WHERE estudio_id=? AND encerrada_em IS NULL",Integer.class,owner.estudio()));
    }

    void inserirHold(long estudio,long recebimento,String tipo,String justificativa,String escopo,Instant iniciada,
            Instant revisar,Instant revisada,Instant encerrada){
        jdbc.update("INSERT INTO retencoes_legais_financeiras(estudio_id,recebimento_origem_id,tipo,justificativa,escopo,iniciada_em,revisar_em,revisada_em,encerrada_em) " +
                "VALUES (?,?,?,?,?,?,?,?,?)",estudio,recebimento,tipo,justificativa,escopo,utc(iniciada),utc(revisar),
                revisada==null?null:utc(revisada),encerrada==null?null:utc(encerrada));
    }
}

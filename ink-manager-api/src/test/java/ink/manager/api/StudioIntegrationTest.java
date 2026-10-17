package ink.manager.api;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
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
 "jwt.secret=test-only-secret-with-more-than-32-bytes","studio.auth-attempts-per-minute=1000","billing.mp-webhook-secret=webhook-secret-for-test-only","billing.sync-initial-delay-ms=3600000"})
class StudioIntegrationTest {
    record Conta(String token,long profissional,long estudio,long usuario,String email){}
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.security.oauth2.jwt.JwtEncoder encoder;
    @MockitoBean RecuperacaoEmail emailSender;
    @MockitoBean ink.manager.api.service.MercadoPagoGateway gateway;
    final HttpClient client=HttpClient.newHttpClient();
    final ObjectMapper mapper=new ObjectMapper();

    @BeforeEach void clean(){
        for(String table:List.of("assinatura_pagamentos","assinatura_checkouts","recuperacoes_senha","marcacoes","bloqueios_agenda","horarios_trabalho","profissionais","cliente","usuarios","estudios")) jdbc.update("DELETE FROM "+table);
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
            "currency_id","BRL","transaction_amount",new java.math.BigDecimal("19.90"),"transaction_amount_refunded",status.equals("refunded")?new java.math.BigDecimal("19.90"):java.math.BigDecimal.ZERO,
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
}

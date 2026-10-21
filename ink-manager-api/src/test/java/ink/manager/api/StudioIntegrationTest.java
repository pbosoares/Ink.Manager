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
 "jwt.secret=test-only-secret-with-more-than-32-bytes","studio.auth-attempts-per-minute=1000","studio.public-bookings-per-minute=1000","studio.public-queries-per-minute=1000","studio.time-zone=America/Sao_Paulo","billing.mp-webhook-secret=webhook-secret-for-test-only","billing.sync-initial-delay-ms=3600000"})
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
        for(String table:List.of("email_action_tokens","ofertas_lista_espera","lista_espera","email_outbox","assinatura_pagamentos","assinatura_checkouts","recuperacoes_senha","recebimentos","marcacoes","bloqueios_agenda","horarios_trabalho","profissionais","cliente","usuarios","servicos","estudios")) jdbc.update("DELETE FROM "+table);
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
        var limite=now.plusMinutes(120).toLocalDateTime();int remainder=limite.getMinute()%15;var slot=limite.plusMinutes(remainder==0?0:15-remainder).withSecond(0).withNano(0);
        if(!slot.toLocalDate().equals(now.toLocalDate())||slot.toLocalTime().isBefore(LocalTime.of(1,0))||slot.toLocalTime().isAfter(LocalTime.of(23,0)))slot=slot.plusDays(1).toLocalDate().atTime(2,0);
        var candidate=new Publico(p.conta(),p.link(),p.servico(),p.profissional(),slot.toLocalDate());
        var limiteExato=now.plusMinutes(105).withSecond(0).withNano(0);
        var tooSoon=pedido(candidate,limiteExato.toLocalTime().toString());tooSoon.put("data",limiteExato.toLocalDate().toString());
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
}

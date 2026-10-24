package ink.manager.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import ink.manager.api.service.RecuperacaoEmail;
import ink.manager.api.service.ManifestoEncerramentoStore;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=${INK_TEST_DB_URL:jdbc:h2:mem:studio;DB_CLOSE_DELAY=-1}",
    "spring.datasource.driver-class-name=${INK_TEST_DB_DRIVER:org.h2.Driver}",
    "spring.datasource.username=${INK_TEST_DB_USER:sa}","spring.datasource.password=${INK_TEST_DB_PASSWORD:}",
    "jwt.secret=test-only-secret-with-more-than-32-bytes","studio.auth-attempts-per-minute=1000",
    "studio.time-zone=America/Sao_Paulo","billing.provider=mercadopago",
    "billing.mp-webhook-secret=webhook-secret-for-test-only","billing.stripe-webhook-secret=stripe-webhook-secret-for-tests",
    "billing.sync-initial-delay-ms=3600000","marcae.retencao.worker-ativo=false",
    "marcae.retencao.lista-espera-horas=5","marcae.retencao.operacional-dias=2",
    "MARCAE_RETENTION_OPERATOR_TOKEN=retention-test-secret-with-more-than-32-bytes",
    "MARCAE_RETENTION_OPERATOR_ID=privacy-operator-test",
    "MARCAE_CLOSURE_MANIFEST_PATH=target/retention-test-manifest.log",
    "MARCAE_CLOSURE_MANIFEST_KEY=closure-test-secret-with-more-than-32-bytes",
    "MAIL_ENABLED=false"})
class RetentionConfigurationIntegrationTest {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ManifestoEncerramentoStore manifest;
    @MockitoBean RecuperacaoEmail emailSender;
    @MockitoBean ink.manager.api.service.MercadoPagoGateway mercadoPago;
    @MockitoBean ink.manager.api.service.StripeGateway stripe;
    final HttpClient client=HttpClient.newHttpClient();
    final ObjectMapper mapper=new ObjectMapper();
    Long estudioId;
    Long otherEstudioId;

    @BeforeEach void resetManifest()throws Exception{
        java.nio.file.Files.deleteIfExists(java.nio.file.Path.of("target/retention-test-manifest.log"));
    }

    HttpResponse<String> request(String method,String path,String body,String token)throws Exception{
        var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(20));
        if(token!=null)builder.header("Authorization","Bearer "+token);
        if(body!=null)builder.header("Content-Type","application/json");
        return client.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> operatorRequest(String method,String path,String body,String token)throws Exception{
        var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(20))
                .header("X-Marcae-Retention-Operator",token);
        if(body!=null)builder.header("Content-Type","application/json");
        return client.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode json(HttpResponse<String> response)throws Exception{return mapper.readTree(response.body());}
    JsonNode json(String body)throws Exception{return mapper.readTree(body);}
    String stringify(Object value)throws Exception{return mapper.writeValueAsString(value);}

    @AfterEach void cleanup(){
        try{java.nio.file.Files.deleteIfExists(java.nio.file.Path.of("target/retention-test-manifest.log"));}catch(java.io.IOException failure){throw new IllegalStateException(failure);}
        cleanupStudy(estudioId);
        cleanupStudy(otherEstudioId);
    }

    private void cleanupStudy(Long id){
        if(id==null)return;
        jdbc.update("DELETE FROM email_action_tokens WHERE estudio_id=?",id);
        jdbc.update("DELETE FROM ofertas_lista_espera WHERE estudio_id=?",id);
        jdbc.update("DELETE FROM lista_espera WHERE estudio_id=?",id);
        jdbc.update("DELETE FROM email_outbox WHERE estudio_id=?",id);
        jdbc.update("DELETE FROM retencoes_legais_financeiras WHERE estudio_id=?",id);
        jdbc.update("DELETE FROM assinatura_pagamentos WHERE estudio_id=?",id);
        jdbc.update("DELETE FROM assinatura_checkouts WHERE estudio_id=?",id);
        jdbc.update("DELETE FROM recuperacoes_senha WHERE usuario_id IN (SELECT id FROM usuarios WHERE estudio_id=?)",id);
        jdbc.update("DELETE FROM recebimentos WHERE estudio_id=?",id);
        jdbc.update("DELETE FROM marcacoes WHERE estudio_id=?",id);
        jdbc.update("DELETE FROM horarios_trabalho WHERE profissional_id IN (SELECT id FROM profissionais WHERE estudio_id=?)",id);
        jdbc.update("DELETE FROM bloqueios_agenda WHERE profissional_id IN (SELECT id FROM profissionais WHERE estudio_id=?)",id);
        jdbc.update("DELETE FROM profissionais WHERE estudio_id=?",id);
        jdbc.update("DELETE FROM cliente WHERE estudio_id=?",id);
        jdbc.update("DELETE FROM servicos WHERE estudio_id=?",id);
        jdbc.update("DELETE FROM recebimentos_arquivo WHERE estudio_id=?",id);
        jdbc.update("DELETE FROM expurgos_estudio WHERE estudio_id=?",id);
        jdbc.update("DELETE FROM usuarios WHERE estudio_id=?",id);
        jdbc.update("DELETE FROM estudios WHERE id=?",id);
    }

    @Test void fechamentoPersistePrazosOperacionaisSemPrazoFinanceiroGlobal()throws Exception{
        String email=UUID.randomUUID()+"@example.com";
        var cadastro=request("POST","/auth/estudios",stringify(Map.of("nomeEstudio","Retencao configuravel","nome","Responsavel","email",email,"senha","SenhaTeste123")),null);
        assertEquals(201,cadastro.statusCode(),cadastro.body());
        estudioId=jdbc.queryForObject("SELECT estudio_id FROM usuarios WHERE email=?",Long.class,email);
        var login=request("POST","/auth/login",stringify(Map.of("email",email,"senha","SenhaTeste123")),null);
        assertEquals(200,login.statusCode(),login.body());String token=json(login).get("token").asText();
        assertEquals(estudioId.longValue(),json(request("GET","/auth/me",null,token)).get("estudio").get("id").asLong());

        var response=request("POST","/estudio/encerramento","{\"confirmacao\":\"ENCERRAR\"}",token);
        assertEquals(202,response.statusCode(),response.body());var result=json(response);
        assertTrue(result.get("arquivoFinanceiroAguardaPrazo").asBoolean());
        Instant requested=Instant.parse(result.get("encerradoEm").asText());
        Instant waitlist=Instant.parse(result.get("expurgoListaEsperaEm").asText());
        Instant operational=Instant.parse(result.get("expurgoOperacionalEm").asText());
        assertEquals(0,Duration.between(requested,waitlist).toSeconds()-5*60*60);
        assertEquals(0,Duration.between(requested,operational).toSeconds()-2*24*60*60);
        var saved=jdbc.queryForMap("SELECT solicitado_em,lista_espera_ate,operacional_ate,recebimentos_arquivo_concluido_em FROM expurgos_estudio WHERE estudio_id=?",estudioId);
        Instant persistedRequest=toInstant(saved.get("solicitado_em"));
        Instant persistedWaitlist=toInstant(saved.get("lista_espera_ate"));
        Instant persistedOperational=toInstant(saved.get("operacional_ate"));
        assertNull(saved.get("recebimentos_arquivo_concluido_em"));
        assertTrue(Math.abs(Duration.between(requested,persistedRequest).toMillis())<=1);
        assertTrue(Math.abs(Duration.between(waitlist,persistedWaitlist).toMillis())<=1);
        assertTrue(Math.abs(Duration.between(operational,persistedOperational).toMillis())<=1);
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM expurgos_estudio WHERE estudio_id=?",Integer.class,estudioId));
        assertEquals(1,manifest.lerTodos().size());
    }

    @Test void holdsSaoAutenticadosIsoladosIdempotentesEAuditaveis()throws Exception{
        String email=UUID.randomUUID()+"@example.com";
        var cadastro=request("POST","/auth/estudios",stringify(Map.of("nomeEstudio","Retencao hold","nome","Responsavel","email",email,"senha","SenhaTeste123")),null);
        assertEquals(201,cadastro.statusCode(),cadastro.body());
        estudioId=jdbc.queryForObject("SELECT estudio_id FROM usuarios WHERE email=?",Long.class,email);
        var login=request("POST","/auth/login",stringify(Map.of("email",email,"senha","SenhaTeste123")),null);
        assertEquals(200,login.statusCode(),login.body());
        String token=json(login).get("token").asText();

        long receiptId=900_000_000L+estudioId;
        Instant received=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        jdbc.update("INSERT INTO recebimentos_arquivo " +
                "(recebimento_origem_id,estudio_id,valor,confirmado_em,moeda,arquivado_em,ano_calendario,expira_em) " +
                "VALUES (?,?,? ,?,'BRL',?,?,?)",
                receiptId,estudioId,new java.math.BigDecimal("29.90"),
                java.time.OffsetDateTime.ofInstant(received,java.time.ZoneOffset.UTC),
                java.time.OffsetDateTime.ofInstant(received,java.time.ZoneOffset.UTC),
                received.atZone(java.time.ZoneId.of("America/Sao_Paulo")).getYear(),
                java.time.OffsetDateTime.ofInstant(received.plusSeconds(86400L*365*6),java.time.ZoneOffset.UTC));

        String base="/estudio/retencoes-financeiras";
        assertEquals(401,request("GET",base,null,null).statusCode());
        Instant review=Instant.now().plusSeconds(20L*86400).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        String createBody=stringify(Map.of("recebimentoId",receiptId,"tipo","PROCESSO_PENDENTE",
                "justificativa","Processo identificado no protocolo interno 2026-4.",
                "escopo","Somente o recebimento indicado.","revisarEm",review));
        var created=request("POST",base,createBody,token);
        assertEquals(201,created.statusCode(),created.body());
        long holdId=json(created).get("id").asLong();
        var repeated=request("POST",base,createBody,token);
        assertEquals(201,repeated.statusCode(),repeated.body());
        assertEquals(holdId,json(repeated).get("id").asLong());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM retencoes_legais_financeiras WHERE estudio_id=? AND recebimento_origem_id=? AND encerrada_em IS NULL",Integer.class,estudioId,receiptId));

        String conflictingBody=stringify(Map.of("recebimentoId",receiptId,"tipo","PROCESSO_PENDENTE",
                "justificativa","Texto diferente.","escopo","Somente o recebimento indicado.","revisarEm",review));
        assertEquals(409,request("POST",base,conflictingBody,token).statusCode());

        Instant nextReview=Instant.now().plusSeconds(40L*86400).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        assertEquals(200,request("POST",base+"/"+holdId+"/revisao",
                stringify(Map.of("revisarEm",nextReview)),token).statusCode());
        assertEquals(2,json(request("GET",base+"/"+holdId+"/historico",null,token)).size());
        String closeBody="{\"confirmacao\":\"ENCERRAR\"}";
        var closed=request("POST",base+"/"+holdId+"/encerramento",closeBody,token);
        assertEquals(200,closed.statusCode(),closed.body());
        assertNotNull(json(closed).get("encerradaEm"));
        assertEquals(200,request("POST",base+"/"+holdId+"/encerramento",closeBody,token).statusCode());
        assertEquals(3,json(request("GET",base+"/"+holdId+"/historico",null,token)).size());

        String otherEmail=UUID.randomUUID()+"@example.com";
        var otherCadastro=request("POST","/auth/estudios",stringify(Map.of("nomeEstudio","Outro estudio","nome","Outra pessoa","email",otherEmail,"senha","SenhaTeste123")),null);
        assertEquals(201,otherCadastro.statusCode(),otherCadastro.body());
        otherEstudioId=jdbc.queryForObject("SELECT estudio_id FROM usuarios WHERE email=?",Long.class,otherEmail);
        var otherLogin=request("POST","/auth/login",stringify(Map.of("email",otherEmail,"senha","SenhaTeste123")),null);
        assertEquals(200,otherLogin.statusCode(),otherLogin.body());
        String otherToken=json(otherLogin).get("token").asText();
        assertEquals(0,json(request("GET",base,null,otherToken)).size());
        assertEquals(404,request("GET",base+"/"+holdId+"/historico",null,otherToken).statusCode());
        assertEquals(404,request("POST",base,stringify(Map.of("recebimentoId",receiptId,"tipo","PROCESSO_PENDENTE",
                "justificativa","Escopo indevido.","escopo","Nao pertence ao estudo.","revisarEm",review)),otherToken).statusCode());
    }

    @Test void operadorRevisaHoldDepoisDoEncerramentoSemReativarConta()throws Exception{
        String email=UUID.randomUUID()+"@example.com";
        var cadastro=request("POST","/auth/estudios",stringify(Map.of("nomeEstudio","Retencao encerrada","nome","Responsavel","email",email,"senha","SenhaTeste123")),null);
        assertEquals(201,cadastro.statusCode(),cadastro.body());
        estudioId=jdbc.queryForObject("SELECT estudio_id FROM usuarios WHERE email=?",Long.class,email);
        var login=request("POST","/auth/login",stringify(Map.of("email",email,"senha","SenhaTeste123")),null);
        assertEquals(200,login.statusCode(),login.body());
        String ownerToken=json(login).get("token").asText();

        long receiptId=910_000_000L+estudioId;
        Instant received=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        jdbc.update("INSERT INTO recebimentos_arquivo " +
                "(recebimento_origem_id,estudio_id,valor,confirmado_em,moeda,arquivado_em,ano_calendario,expira_em) " +
                "VALUES (?,?,?,?,'BRL',?,?,?)",
                receiptId,estudioId,new java.math.BigDecimal("15.00"),
                java.time.OffsetDateTime.ofInstant(received,java.time.ZoneOffset.UTC),
                java.time.OffsetDateTime.ofInstant(received,java.time.ZoneOffset.UTC),
                received.atZone(java.time.ZoneId.of("America/Sao_Paulo")).getYear(),
                java.time.OffsetDateTime.ofInstant(received.plusSeconds(86400L*365*6),java.time.ZoneOffset.UTC));
        String holds="/estudio/retencoes-financeiras";
        Instant review=Instant.now().plusSeconds(30L*86400).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var created=request("POST",holds,stringify(Map.of("recebimentoId",receiptId,"tipo","OBRIGACAO_ESPECIFICA",
                "justificativa","Obrigacao registrada no protocolo A-2026.","escopo","Recebimento indicado.",
                "revisarEm",review)),ownerToken);
        assertEquals(201,created.statusCode(),created.body());
        long holdId=json(created).get("id").asLong();

        var closed=request("POST","/estudio/encerramento","{\"confirmacao\":\"ENCERRAR\"}",ownerToken);
        assertEquals(202,closed.statusCode(),closed.body());
        assertEquals(401,request("GET","/estudio/retencoes-financeiras",null,ownerToken).statusCode());
        String internal="/internal/retencoes-financeiras/estudios/"+estudioId;
        assertEquals(401,operatorRequest("GET",internal,null,"wrong-secret").statusCode());
        assertEquals(401,request("GET",internal,null,null).statusCode());
        String operatorToken="retention-test-secret-with-more-than-32-bytes";

        // Simulate restoring a pre-closure database snapshot while retaining the external manifest.
        jdbc.update("UPDATE estudios SET encerrado_em=NULL, link_publico=? WHERE id=?",
                "restored-old-link-"+estudioId,estudioId);
        jdbc.update("UPDATE usuarios SET ativo=true, token_version=0 WHERE estudio_id=?",estudioId);
        jdbc.update("DELETE FROM expurgos_estudio WHERE estudio_id=?",estudioId);
        var replayed=operatorRequest("POST","/internal/retencoes-financeiras/manifesto/reconciliar",
                null,operatorToken);
        assertEquals(200,replayed.statusCode(),replayed.body());
        assertEquals(1,json(replayed).get("registros").asInt());
        assertEquals(1,json(replayed).get("reconciliados").asInt());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM estudios WHERE id=? AND encerrado_em IS NOT NULL",
                Integer.class,estudioId));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM usuarios WHERE estudio_id=? AND ativo=true",
                Integer.class,estudioId));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM expurgos_estudio WHERE estudio_id=?",
                Integer.class,estudioId));
        assertEquals(401,request("GET",holds,null,ownerToken).statusCode());

        var listed=operatorRequest("GET",internal,null,operatorToken);
        assertEquals(200,listed.statusCode(),listed.body());
        assertEquals(holdId,json(listed.body()).get(0).get("id").asLong());

        Instant nextReview=Instant.now().plusSeconds(60L*86400).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var reviewed=operatorRequest("POST",internal+"/"+holdId+"/revisao",
                stringify(Map.of("revisarEm",nextReview)),operatorToken);
        assertEquals(200,reviewed.statusCode(),reviewed.body());
        var holdClosed=operatorRequest("POST",internal+"/"+holdId+"/encerramento",
                "{\"confirmacao\":\"ENCERRAR\"}",operatorToken);
        assertEquals(200,holdClosed.statusCode(),holdClosed.body());
        assertNotNull(json(holdClosed).get("encerradaEm"));
        var events=operatorRequest("GET",internal+"/"+holdId+"/historico",null,operatorToken);
        assertEquals(3,json(events.body()).size());
        assertEquals("privacy-operator-test",json(events.body()).get(1).get("atorRef").asText());
        assertTrue(json(events.body()).get(1).get("atorUsuarioId").isNull());
        assertEquals(401,operatorRequest("GET",internal,null,"wrong-secret").statusCode());
    }

    private Instant toInstant(Object value){
        if(value instanceof Instant instant)return instant;
        if(value instanceof java.time.OffsetDateTime offset)return offset.toInstant();
        if(value instanceof java.sql.Timestamp timestamp)return timestamp.toInstant();
        throw new AssertionError("Timestamp type inesperado: "+(value==null?"null":value.getClass().getName()));
    }
}

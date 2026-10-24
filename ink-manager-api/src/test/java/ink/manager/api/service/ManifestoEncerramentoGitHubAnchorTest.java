package ink.manager.api.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ink.manager.api.exception.ApiException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class ManifestoEncerramentoGitHubAnchorTest {
    private static final String OWNER = "ink-test-owner";
    private static final String REPOSITORY = "closure-manifest-test";
    private static final String MAIN = "main";
    private static final String ANCHOR = "manifest-anchor";
    private static final String MANIFEST_PATH = "manifest/closures.log";
    private static final String TOKEN = "fake-token-for-local-http-tests-only";
    private static final String KEY = "unit-test-manifest-key-with-more-than-32-bytes";
    private static final String GENESIS_PAYLOAD = "MARCAE-CLOSURE-MANIFEST|v1|GENESIS";
    private static final String ZERO_DIGEST = "0".repeat(64);
    private static final URI FAKE_API = URI.create("https://api.github.test");
    private static final InstantSpec TIMES = new InstantSpec();

    @Test
    void criaGenesisEAncoraNoCommitInicial() throws Exception {
        Fixture fixture = fixture();

        assertTrue(fixture.store().inicializarGitHub());
        assertEquals(fixture.api().mainSha(), fixture.api().anchorSha());
        assertEquals(1, fixture.api().anchorCreations());
        assertTrue(fixture.api().mainContent().startsWith(GENESIS_PAYLOAD + "|"));
        assertTrue(fixture.store().lerTodos().isEmpty());
        assertFalse(fixture.store().inicializarGitHub());
        assertEquals(1, fixture.api().anchorCreations());
    }

    @Test
    void appendAvancaAncoraSomentePorFastForward() throws Exception {
        Fixture fixture = initializedFixture();
        String anchorBefore = fixture.api().anchorSha();

        var tombstone = fixture.store().registrar(71, TIMES.closed(), TIMES.waitlist(), TIMES.operational());

        assertEquals(1, fixture.store().lerTodos().size());
        assertEquals(tombstone, fixture.store().lerTodos().getFirst());
        assertNotEquals(anchorBefore, fixture.api().anchorSha());
        assertEquals(fixture.api().mainSha(), fixture.api().anchorSha());
        assertEquals(1, fixture.api().anchorAdvances());
        assertEquals(List.of(false), fixture.api().anchorForceValues());
        assertTrue(fixture.api().mainContent().startsWith(fixture.api().genesisContent()));
    }

    @Test
    void rejeitaRollbackDeMainParaCommitAnteriorAAncora() throws Exception {
        Fixture fixture = initializedFixture();
        fixture.store().registrar(72, TIMES.closed(), TIMES.waitlist(), TIMES.operational());
        String anchored = fixture.api().anchorSha();
        fixture.api().rollbackMainToParentOf(anchored);

        ApiException failure = assertThrows(ApiException.class, fixture.store()::lerTodos);

        assertEquals(503, failure.getStatus().value());
        assertEquals(anchored, fixture.api().anchorSha());
        assertNotEquals(fixture.api().anchorSha(), fixture.api().mainSha());
    }

    @Test
    void rejeitaTruncamentoDeMainMesmoQuandoCommitEDescendente() throws Exception {
        Fixture fixture = initializedFixture();
        fixture.store().registrar(73, TIMES.closed(), TIMES.waitlist(), TIMES.operational());
        String anchoredContent = fixture.api().contentAt(fixture.api().anchorSha());

        fixture.api().appendExternalCommit(fixture.api().genesisContent());

        ApiException failure = assertThrows(ApiException.class, fixture.store()::lerTodos);

        assertEquals(503, failure.getStatus().value());
        assertEquals(anchoredContent, fixture.api().contentAt(fixture.api().anchorSha()));
        assertFalse(fixture.api().mainContent().startsWith(anchoredContent));
    }

    @Test
    void naoRecriaAncoraAusenteQuandoMainJaContemTombstones() throws Exception {
        Fixture fixture = initializedFixture();
        fixture.store().registrar(74, TIMES.closed(), TIMES.waitlist(), TIMES.operational());
        String mainBefore = fixture.api().mainSha();
        fixture.api().removeAnchor();

        ApiException failure = assertThrows(ApiException.class, fixture.store()::inicializarGitHub);

        assertEquals(503, failure.getStatus().value());
        assertNull(fixture.api().anchorSha());
        assertEquals(mainBefore, fixture.api().mainSha());
        assertEquals(2, fixture.api().mainContent().split("\\n").length);
        assertEquals(1, fixture.api().anchorCreations());
    }

    @Test
    void respostaPerdidaDepoisDoCommitRetornaTombstoneSemDuplicar() throws Exception {
        Fixture fixture = initializedFixture();
        fixture.api().loseNextConditionalPutResponse();

        var committed = fixture.store().registrar(75, TIMES.closed(), TIMES.waitlist(), TIMES.operational());
        var replay = fixture.store().registrar(75, TIMES.closed().plusSeconds(10),
                TIMES.waitlist().plusSeconds(10), TIMES.operational().plusSeconds(10));

        assertEquals(committed, replay);
        assertEquals(1, fixture.store().lerTodos().size());
        assertEquals(1, fixture.api().conditionalPutAttempts());
        assertEquals(fixture.api().mainSha(), fixture.api().anchorSha());
    }

    @Test
    void conflitoConcorrenteDoMesmoEstudioRetornaTombstoneExistente() throws Exception {
        Fixture fixture = initializedFixture();
        var concurrentClosed = TIMES.closed().plusSeconds(5);
        String concurrentLine = signedLine(1, 76, concurrentClosed,
                concurrentClosed.plusSeconds(600), concurrentClosed.plusSeconds(1200), ZERO_DIGEST);
        fixture.api().conflictNextConditionalPutWith(() -> fixture.api().appendExternalCommit(
                fixture.api().genesisContent() + concurrentLine + "\n"));

        var returned = fixture.store().registrar(76, TIMES.closed(), TIMES.waitlist(), TIMES.operational());

        assertEquals(concurrentClosed, returned.encerradoEm());
        assertEquals(1, fixture.store().lerTodos().size());
        assertEquals(1, fixture.api().conditionalPutAttempts());
        assertEquals(fixture.api().mainSha(), fixture.api().anchorSha());
    }

    private static Fixture initializedFixture() throws Exception {
        Fixture fixture = fixture();
        assertTrue(fixture.store().inicializarGitHub());
        return fixture;
    }

    private static Fixture fixture() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        FakeGitHubApi api = new FakeGitHubApi(mapper);
        HttpClient http = mock(HttpClient.class);
        doAnswer(invocation -> api.handle((HttpRequest) invocation.getArgument(0)))
                .when(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        var client = new GitHubManifestoEncerramentoClient(OWNER, REPOSITORY, MAIN, ANCHOR,
                MANIFEST_PATH, TOKEN, FAKE_API, http, mapper);
        var store = new ManifestoEncerramentoStore("", KEY, "github", OWNER, REPOSITORY,
                MAIN, ANCHOR, MANIFEST_PATH, TOKEN, true, mapper);
        ReflectionTestUtils.setField(store, "github", client);
        return new Fixture(store, api);
    }

    private static String signedLine(long sequence, long studioId, java.time.Instant closed,
            java.time.Instant waitlistUntil, java.time.Instant operationalUntil, String previousDigest)
            throws GeneralSecurityException {
        String payload = String.join("|", "v1", Long.toString(sequence), Long.toString(studioId),
                Long.toString(toMicros(closed)), Long.toString(toMicros(waitlistUntil)),
                Long.toString(toMicros(operationalUntil)), previousDigest);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return payload + "|" + java.util.HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private static long toMicros(java.time.Instant instant) {
        return instant.getEpochSecond() * 1_000_000L + instant.getNano() / 1_000L;
    }

    private record Fixture(ManifestoEncerramentoStore store, FakeGitHubApi api) {}

    private record InstantSpec() {
        java.time.Instant closed() { return java.time.Instant.parse("2026-10-24T12:00:00.123456Z"); }
        java.time.Instant waitlist() { return closed().plusSeconds(600); }
        java.time.Instant operational() { return closed().plusSeconds(1200); }
    }

    private record Commit(String sha, String parentSha, String content, String blobSha) {}

    private static final class FakeGitHubApi {
        private static final String REPO_API = "/repos/" + OWNER + "/" + REPOSITORY;

        private final ObjectMapper json;
        private final Map<String, String> refs = new HashMap<>();
        private final Map<String, Commit> commits = new HashMap<>();
        private final List<Boolean> anchorForceValues = new ArrayList<>();
        private int nextCommit = 1;
        private int nextBlob = 1;
        private int anchorCreations;
        private int conditionalPutAttempts;
        private int anchorAdvances;
        private boolean loseNextConditionalPutResponse;
        private Runnable conflictNextConditionalPut;

        private FakeGitHubApi(ObjectMapper json) {
            this.json = json;
            commits.put("commit-0", new Commit("commit-0", null, null, null));
            refs.put(MAIN, "commit-0");
        }

        private HttpResponse<String> handle(HttpRequest request) throws Exception {
            if (!"api.github.test".equals(request.uri().getHost()))
                throw new AssertionError("The fake HTTP test attempted a non-fake host.");
            String path = request.uri().getPath();
            String method = request.method();
            if ("GET".equals(method) && path.equals(REPO_API))
                return response(200, "{\"private\":true,\"archived\":false}");
            if ("GET".equals(method) && path.equals(REPO_API + "/branches/" + MAIN))
                return response(200, "{\"name\":\"main\"}");
            if ("GET".equals(method) && path.startsWith(REPO_API + "/git/ref/heads/"))
                return readRef(path.substring((REPO_API + "/git/ref/heads/").length()));
            if ("GET".equals(method) && path.startsWith(REPO_API + "/contents/"))
                return readContent(query(request.uri(), "ref"));
            if ("GET".equals(method) && path.startsWith(REPO_API + "/compare/"))
                return compare(path.substring((REPO_API + "/compare/").length()));
            if ("POST".equals(method) && path.equals(REPO_API + "/git/refs"))
                return createRef(readJson(request));
            if ("PATCH".equals(method) && path.equals(REPO_API + "/git/refs/heads/" + ANCHOR))
                return advanceAnchor(readJson(request));
            if ("PUT".equals(method) && path.startsWith(REPO_API + "/contents/"))
                return putContent(readJson(request));
            return response(500, "{\"message\":\"Unexpected fake GitHub request\"}");
        }

        private HttpResponse<String> readRef(String branch) {
            String sha = refs.get(branch);
            if (sha == null) return response(404, "{}");
            return response(200, "{\"object\":{\"sha\":\"" + sha + "\"}}");
        }

        private HttpResponse<String> readContent(String commitSha) throws Exception {
            Commit commit = commits.get(commitSha);
            if (commit == null || commit.content() == null) return response(404, "{}");
            byte[] bytes = commit.content().getBytes(StandardCharsets.UTF_8);
            ObjectNode body = json.createObjectNode();
            body.put("type", "file");
            body.put("encoding", "base64");
            body.put("size", bytes.length);
            body.put("sha", commit.blobSha());
            body.put("content", Base64.getEncoder().encodeToString(bytes));
            return response(200, json.writeValueAsString(body));
        }

        private HttpResponse<String> compare(String comparison) {
            String[] sides = comparison.split("\\.\\.\\.", 2);
            if (sides.length != 2) return response(400, "{}");
            int ahead = distance(sides[0], sides[1]);
            int behind = distance(sides[1], sides[0]);
            if (ahead < 0 && behind >= 0) ahead = 0;
            else if (behind < 0 && ahead >= 0) behind = 0;
            else if (ahead < 0) { ahead = 1; behind = 1; }
            return response(200, "{\"ahead_by\":" + ahead + ",\"behind_by\":" + behind + "}");
        }

        private HttpResponse<String> createRef(JsonNode body) {
            String prefix = "refs/heads/";
            String name = body.path("ref").asText("");
            String sha = body.path("sha").asText("");
            if (!name.startsWith(prefix) || !commits.containsKey(sha)) return response(422, "{}");
            String branch = name.substring(prefix.length());
            if (refs.containsKey(branch)) return response(422, "{}");
            refs.put(branch, sha);
            anchorCreations++;
            return response(201, "{}");
        }

        private HttpResponse<String> advanceAnchor(JsonNode body) {
            String current = refs.get(ANCHOR);
            String next = body.path("sha").asText("");
            boolean force = body.path("force").asBoolean(true);
            anchorForceValues.add(force);
            if (force || current == null || distance(current, next) < 0) return response(422, "{}");
            refs.put(ANCHOR, next);
            anchorAdvances++;
            return response(200, "{}");
        }

        private HttpResponse<String> putContent(JsonNode body) throws IOException {
            String branch = body.path("branch").asText("");
            String currentSha = refs.get(branch);
            Commit current = commits.get(currentSha);
            String expectedBlobSha = body.path("sha").asText(null);
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(body.path("content").asText(""));
            } catch (IllegalArgumentException invalid) {
                return response(422, "{}");
            }
            String content = new String(bytes, StandardCharsets.UTF_8);
            if (expectedBlobSha == null || expectedBlobSha.isBlank()) {
                if (current == null || current.content() != null) return response(409, "{}");
                commitMain(branch, content);
                return response(201, "{}");
            }

            conditionalPutAttempts++;
            if (conflictNextConditionalPut != null) {
                Runnable conflict = conflictNextConditionalPut;
                conflictNextConditionalPut = null;
                conflict.run();
                return response(409, "{}");
            }
            current = commits.get(refs.get(branch));
            if (current == null || !expectedBlobSha.equals(current.blobSha())) return response(409, "{}");
            commitMain(branch, content);
            if (loseNextConditionalPutResponse) {
                loseNextConditionalPutResponse = false;
                throw new IOException("Simulated lost HTTP response after remote commit.");
            }
            return response(200, "{}");
        }

        private void commitMain(String branch, String content) {
            String parent = refs.get(branch);
            String sha = "commit-" + nextCommit++;
            String blobSha = "blob-" + nextBlob++;
            commits.put(sha, new Commit(sha, parent, content, blobSha));
            refs.put(branch, sha);
        }

        private void appendExternalCommit(String content) {
            commitMain(MAIN, content);
        }

        private void rollbackMainToParentOf(String commitSha) {
            refs.put(MAIN, commits.get(commitSha).parentSha());
        }

        private void removeAnchor() { refs.remove(ANCHOR); }
        private void loseNextConditionalPutResponse() { loseNextConditionalPutResponse = true; }
        private void conflictNextConditionalPutWith(Runnable concurrentUpdate) {
            conflictNextConditionalPut = concurrentUpdate;
        }
        private String mainSha() { return refs.get(MAIN); }
        private String anchorSha() { return refs.get(ANCHOR); }
        private String mainContent() { return contentAt(mainSha()); }
        private String genesisContent() { return mainContent().substring(0, mainContent().indexOf('\n') + 1); }
        private String contentAt(String sha) {
            Commit commit = commits.get(sha);
            return commit == null ? null : commit.content();
        }
        private int anchorCreations() { return anchorCreations; }
        private int anchorAdvances() { return anchorAdvances; }
        private int conditionalPutAttempts() { return conditionalPutAttempts; }
        private List<Boolean> anchorForceValues() { return List.copyOf(anchorForceValues); }

        private int distance(String ancestor, String descendant) {
            int distance = 0;
            Commit current = commits.get(descendant);
            while (current != null) {
                if (current.sha().equals(ancestor)) return distance;
                current = current.parentSha() == null ? null : commits.get(current.parentSha());
                distance++;
            }
            return -1;
        }

        private JsonNode readJson(HttpRequest request) throws Exception {
            return json.readTree(readBody(request));
        }

        private static String readBody(HttpRequest request) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            CompletableFuture<Void> finished = new CompletableFuture<>();
            request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
                @Override public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }
                @Override public void onNext(ByteBuffer item) {
                    byte[] chunk = new byte[item.remaining()];
                    item.get(chunk);
                    bytes.write(chunk, 0, chunk.length);
                }
                @Override public void onError(Throwable failure) { finished.completeExceptionally(failure); }
                @Override public void onComplete() { finished.complete(null); }
            });
            try {
                finished.join();
            } catch (RuntimeException failure) {
                throw new IOException("Could not read fake HTTP request body.", failure);
            }
            return bytes.toString(StandardCharsets.UTF_8);
        }

        private static String query(URI uri, String name) throws IOException {
            String rawQuery = uri.getRawQuery();
            if (rawQuery == null) return "";
            for (String pair : rawQuery.split("&")) {
                String[] values = pair.split("=", 2);
                if (values.length == 2 && name.equals(URLDecoder.decode(values[0], StandardCharsets.UTF_8)))
                    return URLDecoder.decode(values[1], StandardCharsets.UTF_8);
            }
            return "";
        }

        @SuppressWarnings("unchecked")
        private static HttpResponse<String> response(int status, String body) {
            HttpResponse<String> response = (HttpResponse<String>) mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(status);
            when(response.body()).thenReturn(body);
            return response;
        }
    }
}

package ink.manager.api.service;

import ink.manager.api.exception.ApiException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import org.springframework.http.HttpStatus;

/** Narrow GitHub API client for the private, append-only closure ledger. */
final class GitHubManifestoEncerramentoClient {
    private static final String API_VERSION = "2026-03-10";
    private static final int MAX_CONTENT_BYTES = 900_000;
    private static final int MAX_REF_RETRIES = 5;

    record Snapshot(String content, String sha, String commitSha) {}
    private record Comparison(int aheadBy, int behindBy) {
        boolean identical() { return aheadBy == 0 && behindBy == 0; }
        boolean headIsAhead() { return aheadBy > 0 && behindBy == 0; }
    }
    private record BranchRef(String commitSha) {}

    private final String owner;
    private final String repository;
    private final String branch;
    private final String anchorBranch;
    private final String path;
    private final String token;
    private final URI apiBase;
    private final HttpClient http;
    private final ObjectMapper json;

    GitHubManifestoEncerramentoClient(String owner, String repository, String branch, String anchorBranch,
            String path, String token, ObjectMapper json) {
        this(owner, repository, branch, anchorBranch, path, token, URI.create("https://api.github.com"),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), json);
    }

    GitHubManifestoEncerramentoClient(String owner, String repository, String branch, String anchorBranch,
            String path, String token, URI apiBase, HttpClient http, ObjectMapper json) {
        this.owner = clean(owner);
        this.repository = clean(repository);
        this.branch = clean(branch);
        this.anchorBranch = clean(anchorBranch);
        this.path = clean(path);
        this.token = token == null ? "" : token.trim();
        this.apiBase = apiBase;
        this.http = http;
        this.json = json;
    }

    boolean configurado() {
        return owner.matches("[A-Za-z0-9-]+") && repository.matches("[A-Za-z0-9._-]+")
                && branch.matches("[A-Za-z0-9._-]+") && !branch.contains("..")
                && anchorBranch.matches("[A-Za-z0-9._-]+") && !anchorBranch.contains("..")
                && !branch.equals(anchorBranch)
                && path.matches("[A-Za-z0-9._/-]+") && !path.startsWith("/") && !path.contains("..")
                && token.length() >= 20 && apiBase != null && "https".equalsIgnoreCase(apiBase.getScheme());
    }

    Optional<Snapshot> read() {
        ensureConfigured();
        for (int tentativa = 0; tentativa < MAX_REF_RETRIES; tentativa++) {
            Snapshot current = readMain().orElse(null);
            if (current == null) return Optional.empty();
            BranchRef anchor = readRef(anchorBranch).orElseThrow(GitHubManifestoEncerramentoClient::unavailable);
            Snapshot anchored = readContent(anchor.commitSha()).orElseThrow(
                    GitHubManifestoEncerramentoClient::unavailable);
            Comparison comparison = compare(anchor.commitSha(), current.commitSha());
            if (comparison.identical()) {
                if (!current.content().equals(anchored.content())) throw unavailable();
                return Optional.of(current);
            }
            if (!comparison.headIsAhead() || !current.content().startsWith(anchored.content()))
                throw unavailable();

            try {
                advanceAnchor(anchor.commitSha(), current.commitSha());
                continue;
            } catch (Conflict conflict) {
                // Another writer may have advanced the anchor; compare again from fresh refs.
            }
        }
        throw unavailable();
    }

    /** Reads the main ledger without trusting or advancing the monotonic anchor. Bootstrap only. */
    Optional<Snapshot> readMain() {
        ensureConfigured();
        BranchRef head = readRef(branch).orElseThrow(GitHubManifestoEncerramentoClient::unavailable);
        return readContent(head.commitSha());
    }

    Optional<Snapshot> readAnchor() {
        ensureConfigured();
        return readRef(anchorBranch).map(ref -> readContent(ref.commitSha())
                .orElseThrow(GitHubManifestoEncerramentoClient::unavailable));
    }

    void initializeAnchor(String commitSha) {
        ensureConfigured();
        try {
            ObjectNode body = json.createObjectNode();
            body.put("ref", "refs/heads/" + anchorBranch);
            body.put("sha", commitSha);
            HttpResponse<String> response = send(request(apiBase.resolve(
                    "/repos/" + owner + "/" + repository + "/git/refs"), "application/vnd.github+json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build());
            if (response.statusCode() == 409 || response.statusCode() == 422) throw new Conflict();
            requireStatus(response, 201);
        } catch (tools.jackson.core.JacksonException failure) {
            throw unavailable();
        }
    }

    void create(String content) {
        put(content, null);
    }

    void update(String content, String sha) {
        if (sha == null || sha.isBlank()) throw unavailable();
        put(content, sha);
    }

    void validatePrivateRepositoryAndBranch() {
        ensureConfigured();
        HttpResponse<String> repositoryResponse = send(request(
                apiBase.resolve("/repos/" + owner + "/" + repository), "application/vnd.github+json")
                .GET().build());
        requireStatus(repositoryResponse, 200);
        try {
            JsonNode details = json.readTree(repositoryResponse.body());
            if (!details.path("private").asBoolean(false) || details.path("archived").asBoolean(true))
                throw unavailable();
        } catch (RuntimeException failure) {
            throw unavailable();
        }

        HttpResponse<String> branchResponse = send(request(apiBase.resolve(
                "/repos/" + owner + "/" + repository + "/branches/" + encode(branch)),
                "application/vnd.github+json").GET().build());
        requireStatus(branchResponse, 200);
    }

    private Optional<Snapshot> readContent(String commitSha) {
        HttpResponse<String> response = send(request(contentsUri(commitSha), "application/vnd.github+json")
                .GET().build());
        if (response.statusCode() == 404) return Optional.empty();
        requireStatus(response, 200);
        try {
            JsonNode body = json.readTree(response.body());
            if (!"file".equals(body.path("type").asText())
                    || !"base64".equals(body.path("encoding").asText()))
                throw unavailable();
            long size = body.path("size").asLong(-1);
            String sha = body.path("sha").asText("");
            if (size < 0 || size > MAX_CONTENT_BYTES || sha.isBlank()) throw unavailable();
            String encoded = body.path("content").asText("").replaceAll("\\s", "");
            byte[] bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length != size || bytes.length > MAX_CONTENT_BYTES) throw unavailable();
            return Optional.of(new Snapshot(new String(bytes, StandardCharsets.UTF_8), sha, commitSha));
        } catch (RuntimeException failure) {
            throw unavailable();
        }
    }

    private Optional<BranchRef> readRef(String ref) {
        HttpResponse<String> response = send(request(apiBase.resolve("/repos/" + owner + "/" + repository
                + "/git/ref/heads/" + encode(ref)), "application/vnd.github+json").GET().build());
        if (response.statusCode() == 404) return Optional.empty();
        requireStatus(response, 200);
        try {
            String sha = json.readTree(response.body()).path("object").path("sha").asText("");
            if (sha.isBlank()) throw unavailable();
            return Optional.of(new BranchRef(sha));
        } catch (RuntimeException failure) {
            throw unavailable();
        }
    }

    private Comparison compare(String base, String head) {
        HttpResponse<String> response = send(request(apiBase.resolve("/repos/" + owner + "/" + repository
                + "/compare/" + encode(base) + "..." + encode(head)), "application/vnd.github+json")
                .GET().build());
        requireStatus(response, 200);
        try {
            JsonNode body = json.readTree(response.body());
            int aheadBy = body.path("ahead_by").asInt(-1);
            int behindBy = body.path("behind_by").asInt(-1);
            if (aheadBy < 0 || behindBy < 0) throw unavailable();
            return new Comparison(aheadBy, behindBy);
        } catch (RuntimeException failure) {
            throw unavailable();
        }
    }

    private void advanceAnchor(String expectedAnchor, String nextCommit) {
        try {
            ObjectNode body = json.createObjectNode();
            body.put("sha", nextCommit);
            body.put("force", false);
            HttpResponse<String> response = send(request(apiBase.resolve("/repos/" + owner + "/" + repository
                    + "/git/refs/heads/" + encode(anchorBranch)), "application/vnd.github+json")
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(
                            json.writeValueAsString(body), StandardCharsets.UTF_8)).build());
            if (response.statusCode() == 409 || response.statusCode() == 422) throw new Conflict();
            requireStatus(response, 200);
            BranchRef confirmed = readRef(anchorBranch).orElseThrow(GitHubManifestoEncerramentoClient::unavailable);
            Comparison comparison = compare(expectedAnchor, confirmed.commitSha());
            if (!comparison.identical() && !comparison.headIsAhead()) throw unavailable();
        } catch (tools.jackson.core.JacksonException failure) {
            throw unavailable();
        }
    }

    private void put(String content, String sha) {
        ensureConfigured();
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_CONTENT_BYTES) throw unavailable();
        try {
            ObjectNode body = json.createObjectNode();
            body.put("message", "Marcae closure manifest update");
            body.put("content", Base64.getEncoder().encodeToString(bytes));
            body.put("branch", branch);
            body.putObject("committer").put("name", "Marcae Retention").put(
                    "email", "marcae-retention@users.noreply.github.com");
            if (sha != null) body.put("sha", sha);
            HttpRequest request = request(contentsUri(branch), "application/vnd.github+json")
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = send(request);
            if (response.statusCode() == 409) throw new Conflict();
            if (response.statusCode() != 200 && response.statusCode() != 201) throw unavailable();
        } catch (tools.jackson.core.JacksonException failure) {
            throw unavailable();
        }
    }

    private URI contentsUri(String ref) {
        String encodedPath = java.util.Arrays.stream(path.split("/"))
                .map(GitHubManifestoEncerramentoClient::encode)
                .reduce((left, right) -> left + "/" + right).orElseThrow();
        return apiBase.resolve("/repos/" + owner + "/" + repository + "/contents/" + encodedPath
                + "?ref=" + encode(ref));
    }

    private HttpRequest.Builder request(URI uri, String accept) {
        return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                .header("Accept", accept)
                .header("Authorization", "Bearer " + token)
                .header("X-GitHub-Api-Version", API_VERSION)
                .header("User-Agent", "Marcae-Retention-Manifest");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException failure) {
            throw unavailable();
        }
    }

    private static void requireStatus(HttpResponse<String> response, int expected) {
        if (response.statusCode() != expected) throw unavailable();
    }

    private void ensureConfigured() {
        if (!configurado()) throw unavailable();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "Manifesto externo de encerramentos indisponivel; nenhum dado foi removido.");
    }

    static final class Conflict extends RuntimeException {
        private Conflict() {
            super("External closure manifest changed concurrently.");
        }
    }
}

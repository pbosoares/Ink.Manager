package ink.manager.api.service;

import ink.manager.api.exception.ApiException;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Signed append-only closure manifest outside PostgreSQL. The configured path must be a
 * durable mount outside the database volume; an unconfigured store fails closed.
 */
@Component
public class ManifestoEncerramentoStore {
    private static final String VERSION = "v1";
    private static final String BACKEND_FILE = "file";
    private static final String BACKEND_GITHUB = "github";
    private static final String GENESIS_PAYLOAD = "MARCAE-CLOSURE-MANIFEST|v1|GENESIS";
    private static final String ZERO_DIGEST = "0".repeat(64);
    private static final int MAX_BYTES = 64 * 1024 * 1024;
    private static final int MAX_GITHUB_BYTES = 900_000;

    public record Tombstone(long sequencia, long estudioId, Instant encerradoEm,
            Instant listaEsperaAte, Instant operacionalAte, String digestAnterior, String digestAtual) {}

    private final Path arquivo;
    private final byte[] chave;
    private final String backend;
    private final GitHubManifestoEncerramentoClient github;
    private final boolean inicializacaoPermitida;

    @Autowired
    public ManifestoEncerramentoStore(
            @Value("${MARCAE_CLOSURE_MANIFEST_PATH:}") String arquivo,
            @Value("${MARCAE_CLOSURE_MANIFEST_KEY:}") String chave,
            @Value("${MARCAE_CLOSURE_MANIFEST_BACKEND:file}") String backend,
            @Value("${MARCAE_CLOSURE_GITHUB_OWNER:}") String owner,
            @Value("${MARCAE_CLOSURE_GITHUB_REPOSITORY:}") String repository,
            @Value("${MARCAE_CLOSURE_GITHUB_BRANCH:main}") String branch,
            @Value("${MARCAE_CLOSURE_GITHUB_ANCHOR_BRANCH:manifest-anchor}") String anchorBranch,
            @Value("${MARCAE_CLOSURE_GITHUB_PATH:manifest/closures.log}") String path,
            @Value("${MARCAE_CLOSURE_GITHUB_TOKEN:}") String token,
            @Value("${MARCAE_CLOSURE_MANIFEST_ALLOW_INITIALIZE:false}") boolean inicializacaoPermitida,
            ObjectMapper objectMapper) {
        this.arquivo = arquivo == null || arquivo.isBlank() ? null : Path.of(arquivo).toAbsolutePath().normalize();
        this.chave = chave == null ? new byte[0] : chave.getBytes(StandardCharsets.UTF_8);
        this.backend = backend == null ? "" : backend.trim().toLowerCase(java.util.Locale.ROOT);
        this.github = new GitHubManifestoEncerramentoClient(owner, repository, branch, anchorBranch,
                path, token, objectMapper);
        this.inicializacaoPermitida = inicializacaoPermitida;
    }

    /** Local-file constructor retained for isolated unit tests and development. */
    public ManifestoEncerramentoStore(String arquivo, String chave) {
        this.arquivo = arquivo == null || arquivo.isBlank() ? null : Path.of(arquivo).toAbsolutePath().normalize();
        this.chave = chave == null ? new byte[0] : chave.getBytes(StandardCharsets.UTF_8);
        this.backend = BACKEND_FILE;
        this.github = null;
        this.inicializacaoPermitida = false;
    }

    public boolean configurado() {
        if (chave.length < 32) return false;
        return switch (backend) {
            case BACKEND_FILE -> arquivo != null;
            case BACKEND_GITHUB -> github != null && github.configurado();
            default -> false;
        };
    }

    public boolean exigeReconciliacaoNaInicializacao() {
        return BACKEND_GITHUB.equals(backend);
    }

    public synchronized Tombstone registrar(long estudioId, Instant encerradoEm,
            Instant listaEsperaAte, Instant operacionalAte) {
        exigirConfigurado();
        if (estudioId < 1 || !listaEsperaAte.isAfter(encerradoEm) || !operacionalAte.isAfter(listaEsperaAte))
            throw new IllegalArgumentException("Manifesto de encerramento recebeu datas ou ID invalidos.");
        if (BACKEND_GITHUB.equals(backend))
            return registrarNoGitHub(estudioId, encerradoEm, listaEsperaAte, operacionalAte);
        return registrarNoArquivo(estudioId, encerradoEm, listaEsperaAte, operacionalAte);
    }

    private Tombstone registrarNoArquivo(long estudioId, Instant encerradoEm,
            Instant listaEsperaAte, Instant operacionalAte) {
        Path diretorio = arquivo.getParent();
        if (diretorio == null || !Files.isDirectory(diretorio))
            throw indisponivel();

        try (FileChannel channel = FileChannel.open(arquivo,
                StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
                FileLock ignored = channel.lock()) {
            protegerPermissoes();
            List<Tombstone> tombstones = ler(channel);
            for (Tombstone tombstone : tombstones) {
                if (tombstone.estudioId() == estudioId) return tombstone;
            }
            String anterior = tombstones.isEmpty() ? ZERO_DIGEST : tombstones.getLast().digestAtual();
            Tombstone novo = new Tombstone(tombstones.size() + 1L, estudioId,
                    micros(encerradoEm), micros(listaEsperaAte), micros(operacionalAte), anterior, "");
            String payload = payload(novo);
            String assinatura = hmac(payload);
            String linha = payload + "|" + assinatura;
            String digest = sha256(linha);
            Tombstone persistido = new Tombstone(novo.sequencia(), novo.estudioId(), novo.encerradoEm(),
                    novo.listaEsperaAte(), novo.operacionalAte(), novo.digestAnterior(), digest);
            channel.position(channel.size());
            ByteBuffer bytes = StandardCharsets.UTF_8.encode(linha + "\n");
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
            return persistido;
        } catch (IOException | GeneralSecurityException | RuntimeException failure) {
            if (failure instanceof ApiException api) throw api;
            throw indisponivel();
        }
    }

    public synchronized List<Tombstone> lerTodos() {
        exigirConfigurado();
        if (BACKEND_GITHUB.equals(backend)) {
            GitHubManifestoEncerramentoClient.Snapshot snapshot = github.read().orElseThrow(ManifestoEncerramentoStore::indisponivel);
            return List.copyOf(validarConteudoExterno(snapshot.content()));
        }
        if (!Files.exists(arquivo)) return List.of();
        try (FileChannel channel = FileChannel.open(arquivo, StandardOpenOption.READ);
                FileLock ignored = channel.lock(0L, Long.MAX_VALUE, true)) {
            return List.copyOf(ler(channel));
        } catch (IOException | GeneralSecurityException | RuntimeException failure) {
            if (failure instanceof ApiException api) throw api;
            throw new IllegalStateException("Manifesto de encerramento ausente, corrompido ou inacessivel.", failure);
        }
    }

    /** One-time bootstrap; the endpoint is operator-authenticated and disabled by default. */
    public synchronized boolean inicializarGitHub() {
        exigirConfigurado();
        if (!BACKEND_GITHUB.equals(backend) || !inicializacaoPermitida)
            throw new ApiException(HttpStatus.FORBIDDEN, "Inicializacao do manifesto externo nao esta habilitada.");
        github.validatePrivateRepositoryAndBranch();
        Optional<GitHubManifestoEncerramentoClient.Snapshot> anchor = github.readAnchor();
        if (anchor.isPresent()) {
            GitHubManifestoEncerramentoClient.Snapshot atual = github.read()
                    .orElseThrow(ManifestoEncerramentoStore::indisponivel);
            validarConteudoExterno(atual.content());
            return false;
        }

        Optional<GitHubManifestoEncerramentoClient.Snapshot> main = github.readMain();
        boolean criado = false;
        if (main.isEmpty()) {
            try {
                github.create(genesisLine() + "\n");
                criado = true;
            } catch (GitHubManifestoEncerramentoClient.Conflict collision) {
                // Another initializer may have created the genesis concurrently.
            } catch (GeneralSecurityException failure) {
                throw indisponivel();
            }
            main = github.readMain();
        }
        GitHubManifestoEncerramentoClient.Snapshot ledger = main
                .orElseThrow(ManifestoEncerramentoStore::indisponivel);
        List<Tombstone> tombstones = validarConteudoExterno(ledger.content());
        if (!tombstones.isEmpty())
            throw indisponivel(); // A missing anchor must never silently trust a ledger with closures.
        try {
            github.initializeAnchor(ledger.commitSha());
        } catch (GitHubManifestoEncerramentoClient.Conflict collision) {
            // Another initializer may have created the anchor; normal validation below decides.
        }
        GitHubManifestoEncerramentoClient.Snapshot confirmado = github.read()
                .orElseThrow(ManifestoEncerramentoStore::indisponivel);
        validarConteudoExterno(confirmado.content());
        return criado;
    }

    private Tombstone registrarNoGitHub(long estudioId, Instant encerradoEm,
            Instant listaEsperaAte, Instant operacionalAte) {
        for (int tentativa = 0; tentativa < 5; tentativa++) {
            GitHubManifestoEncerramentoClient.Snapshot snapshot = github.read()
                    .orElseThrow(ManifestoEncerramentoStore::indisponivel);
            List<Tombstone> existentes = validarConteudoExterno(snapshot.content());
            for (Tombstone tombstone : existentes) {
                if (tombstone.estudioId() == estudioId) return tombstone;
            }

            try {
                Tombstone novo = novoTombstone(existentes.size() + 1L, estudioId, encerradoEm,
                        listaEsperaAte, operacionalAte,
                        existentes.isEmpty() ? ZERO_DIGEST : existentes.getLast().digestAtual());
                String atualizado = snapshot.content() + linha(novo) + "\n";
                try {
                    github.update(atualizado, snapshot.sha());
                } catch (ApiException uncertainWrite) {
                    // The remote commit may have succeeded even if its HTTP response was lost.
                    GitHubManifestoEncerramentoClient.Snapshot afterFailure = github.read()
                            .orElseThrow(() -> uncertainWrite);
                    for (Tombstone tombstone : validarConteudoExterno(afterFailure.content())) {
                        if (tombstone.estudioId() == estudioId) return tombstone;
                    }
                    throw uncertainWrite;
                }
                GitHubManifestoEncerramentoClient.Snapshot confirmado = github.read()
                        .orElseThrow(ManifestoEncerramentoStore::indisponivel);
                for (Tombstone tombstone : validarConteudoExterno(confirmado.content())) {
                    if (tombstone.estudioId() == estudioId) return tombstone;
                }
                throw indisponivel();
            } catch (GitHubManifestoEncerramentoClient.Conflict conflict) {
                // Re-read the GitHub blob SHA and retry with the latest complete chain.
            } catch (GeneralSecurityException failure) {
                throw indisponivel();
            }
        }
        throw indisponivel();
    }

    private List<Tombstone> validarConteudoExterno(String content) {
        try {
            if (content.getBytes(StandardCharsets.UTF_8).length > MAX_GITHUB_BYTES)
                throw new IOException("Manifesto externo acima do limite do backend GitHub.");
            return lerConteudo(content, true);
        } catch (IOException | GeneralSecurityException | RuntimeException failure) {
            if (failure instanceof ApiException api) throw api;
            throw indisponivel();
        }
    }

    private List<Tombstone> ler(FileChannel channel) throws IOException, GeneralSecurityException {
        long size = channel.size();
        if (size > MAX_BYTES) throw new IOException("Manifesto acima do limite.");
        if (size == 0) return List.of();
        ByteBuffer buffer = ByteBuffer.allocate((int) size);
        channel.position(0);
        while (buffer.hasRemaining() && channel.read(buffer) >= 0) { }
        String conteudo = StandardCharsets.UTF_8.decode(buffer.flip()).toString();
        return lerConteudo(conteudo, false);
    }

    private List<Tombstone> lerConteudo(String conteudo, boolean exigirGenesis)
            throws IOException, GeneralSecurityException {
        if (conteudo.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IOException("Manifesto acima do limite.");
        if (!conteudo.endsWith("\n")) throw new IOException("Manifesto truncado.");

        List<Tombstone> resultado = new ArrayList<>();
        Set<Long> estudios = new HashSet<>();
        String digestAnterior = ZERO_DIGEST;
        String[] linhas = conteudo.substring(0, conteudo.length() - 1).split("\n", -1);
        int inicio = 0;
        if (exigirGenesis) {
            if (linhas.length == 0 || !genesisLine().equals(linhas[0]))
                throw new IOException("Genesis do manifesto externo ausente ou invalido.");
            inicio = 1;
        }
        for (int i = inicio; i < linhas.length; i++) {
            String[] campos = linhas[i].split("\\|", -1);
            if (campos.length != 8 || !VERSION.equals(campos[0]))
                throw new IOException("Formato de manifesto invalido.");
            long sequencia = Long.parseLong(campos[1]);
            long estudioId = Long.parseLong(campos[2]);
            Instant encerrado = fromMicros(Long.parseLong(campos[3]));
            Instant filaAte = fromMicros(Long.parseLong(campos[4]));
            Instant operacionalAte = fromMicros(Long.parseLong(campos[5]));
            String anterior = campos[6];
            String assinatura = campos[7];
            String payload = String.join("|", java.util.Arrays.copyOf(campos, 7));
            if (sequencia != i - inicio + 1L || estudioId < 1 || !estudios.add(estudioId)
                    || !digestAnterior.equals(anterior)
                    || !filaAte.isAfter(encerrado) || !operacionalAte.isAfter(filaAte)
                    || !MessageDigest.isEqual(hmac(payload).getBytes(StandardCharsets.US_ASCII),
                            assinatura.getBytes(StandardCharsets.US_ASCII)))
                throw new IOException("Assinatura ou encadeamento do manifesto invalido.");
            String digest = sha256(linhas[i]);
            resultado.add(new Tombstone(sequencia, estudioId, encerrado, filaAte, operacionalAte, anterior, digest));
            digestAnterior = digest;
        }
        return resultado;
    }

    private Tombstone novoTombstone(long sequencia, long estudioId, Instant encerradoEm,
            Instant listaEsperaAte, Instant operacionalAte, String anterior) throws GeneralSecurityException {
        Tombstone novo = new Tombstone(sequencia, estudioId, micros(encerradoEm),
                micros(listaEsperaAte), micros(operacionalAte), anterior, "");
        String line = linha(novo);
        return new Tombstone(novo.sequencia(), novo.estudioId(), novo.encerradoEm(),
                novo.listaEsperaAte(), novo.operacionalAte(), novo.digestAnterior(), sha256(line));
    }

    private String linha(Tombstone tombstone) throws GeneralSecurityException {
        String payload = payload(tombstone);
        return payload + "|" + hmac(payload);
    }

    private String genesisLine() throws GeneralSecurityException {
        return GENESIS_PAYLOAD + "|" + hmac(GENESIS_PAYLOAD);
    }

    private void protegerPermissoes() throws IOException {
        try {
            Files.setPosixFilePermissions(arquivo, Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Windows test hosts do not expose POSIX permissions; production is Linux.
        }
    }

    private String payload(Tombstone tombstone) {
        return String.join("|", VERSION, Long.toString(tombstone.sequencia()),
                Long.toString(tombstone.estudioId()), Long.toString(toMicros(tombstone.encerradoEm())),
                Long.toString(toMicros(tombstone.listaEsperaAte())),
                Long.toString(toMicros(tombstone.operacionalAte())), tombstone.digestAnterior());
    }

    private String hmac(String value) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(chave, "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String sha256(String value) throws GeneralSecurityException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private void exigirConfigurado() {
        if (!configurado()) throw indisponivel();
    }

    private static ApiException indisponivel() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "Manifesto externo de encerramentos indisponivel; nenhum dado foi removido.");
    }

    private static Instant micros(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS);
    }

    private static long toMicros(Instant instant) {
        return instant.getEpochSecond() * 1_000_000L + instant.getNano() / 1_000L;
    }

    private static Instant fromMicros(long micros) {
        long seconds = Math.floorDiv(micros, 1_000_000L);
        long nanos = Math.floorMod(micros, 1_000_000L) * 1_000L;
        return Instant.ofEpochSecond(seconds, nanos);
    }
}

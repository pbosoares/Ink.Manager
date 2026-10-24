package ink.manager.api.service;

import static org.junit.jupiter.api.Assertions.*;

import ink.manager.api.exception.ApiException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManifestoEncerramentoStoreTest {
    private static final String KEY = "unit-test-manifest-key-with-more-than-32-bytes";

    @TempDir Path directory;

    @Test
    void gravaEncadeiaEReutilizaTombstonePorEstudio() {
        var store = new ManifestoEncerramentoStore(directory.resolve("closure.log").toString(), KEY);
        Instant closed = Instant.parse("2026-10-24T12:00:00.123456Z");
        Instant waitlist = closed.plusSeconds(86400);
        Instant operational = closed.plusSeconds(30L * 86400);

        var first = store.registrar(7, closed, waitlist, operational);
        var replay = store.registrar(7, closed.plusSeconds(5), waitlist.plusSeconds(5), operational.plusSeconds(5));
        var second = store.registrar(8, closed, waitlist, operational);

        assertEquals(first, replay);
        assertEquals(1, first.sequencia());
        assertEquals(2, second.sequencia());
        assertEquals(first.digestAtual(), second.digestAnterior());
        assertEquals(2, store.lerTodos().size());
        assertEquals(closed, store.lerTodos().getFirst().encerradoEm());
    }

    @Test
    void rejeitaManifestoAlterado() throws Exception {
        Path file = directory.resolve("closure.log");
        var store = new ManifestoEncerramentoStore(file.toString(), KEY);
        Instant closed = Instant.parse("2026-10-24T12:00:00Z");
        store.registrar(7, closed, closed.plusSeconds(60), closed.plusSeconds(120));

        String line = Files.readString(file).trim();
        char last = line.charAt(line.length() - 1);
        char replacement = last == '0' ? '1' : '0';
        Files.writeString(file, line.substring(0, line.length() - 1) + replacement + "\n");

        assertThrows(IllegalStateException.class, store::lerTodos);
    }

    @Test
    void fechamentoFalhaFechadoSemDestinoOuChave() {
        var store = new ManifestoEncerramentoStore("", "");
        Instant closed = Instant.parse("2026-10-24T12:00:00Z");
        var failure = assertThrows(ApiException.class,
                () -> store.registrar(7, closed, closed.plusSeconds(60), closed.plusSeconds(120)));
        assertEquals(503, failure.getStatus().value());
    }
}

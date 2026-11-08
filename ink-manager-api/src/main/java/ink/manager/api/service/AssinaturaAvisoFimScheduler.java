package ink.manager.api.service;

import ink.manager.api.model.Perfil;
import ink.manager.api.model.Usuario;
import ink.manager.api.repository.EstudioRepository;
import ink.manager.api.repository.UsuarioRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Enqueues one owner reminder for the server-calculated trial deadline. */
@Component
public class AssinaturaAvisoFimScheduler {
    private static final String TIPO = "AVISO_FIM_AVALIACAO_24H";
    private final EstudioRepository estudios;
    private final UsuarioRepository usuarios;
    private final EmailOutboxService outbox;
    private final Clock clock;
    private final int trialDays;

    public AssinaturaAvisoFimScheduler(EstudioRepository estudios, UsuarioRepository usuarios,
            EmailOutboxService outbox, Clock clock,
            @Value("$" + "{billing.trial-days:14}") int trialDays) {
        this.estudios = estudios;
        this.usuarios = usuarios;
        this.outbox = outbox;
        this.clock = clock;
        this.trialDays = trialDays;
    }

    @Scheduled(fixedDelayString = "$" + "{billing.trial-reminder.scan-interval-ms:60000}")
    @Transactional
    public void enfileirarAvisos() {
        if (trialDays < 1) return;
        LocalDateTime agora = LocalDateTime.now(clock);
        var candidatos = estudios.findByEncerradoEmIsNullAndCriadoEmBetween(
                agora.minusDays(trialDays), agora.plusHours(24).minusDays(trialDays));
        for (var candidato : candidatos) {
            var estudio = estudios.bloquear(candidato.getId()).orElse(null);
            if (estudio == null || estudio.isEncerrado()) continue;
            LocalDateTime fimAvaliacao = estudio.getCriadoEm().plusDays(trialDays);
            if (fimAvaliacao.isBefore(estudio.getCriadoEm().plusHours(24))
                    || agora.isBefore(fimAvaliacao.minusHours(24)) || !agora.isBefore(fimAvaliacao)) continue;
            if (estudio.getPagoAte() != null && estudio.getPagoAte().isAfter(agora)) continue;

            String destinatario = usuarios.findByEstudioIdOrderByNomeAsc(estudio.getId()).stream()
                    .filter(Usuario::isAtivo)
                    .filter(usuario -> usuario.getPerfil() == Perfil.PROPRIETARIO)
                    .map(Usuario::getEmail)
                    .filter(email -> email != null && !email.isBlank())
                    .findFirst().orElse(null);
            if (destinatario == null) continue;

            String chave = TIPO + ":" + estudio.getId() + ":" + fimAvaliacao;
            String dados = "{\"avaliacaoAte\":\"" + fimAvaliacao + "\"}";
            outbox.enqueue(estudio.getId(), destinatario, TIPO, chave, clock.instant(),
                    fimAvaliacao.toString(), dados);
        }
    }
}
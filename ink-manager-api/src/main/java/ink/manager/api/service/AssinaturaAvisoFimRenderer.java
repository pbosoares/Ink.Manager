package ink.manager.api.service;

import ink.manager.api.model.Perfil;
import ink.manager.api.model.Usuario;
import ink.manager.api.repository.EstudioRepository;
import ink.manager.api.repository.UsuarioRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AssinaturaAvisoFimRenderer implements EmailOutboxRenderer {
    private static final String TIPO = "AVISO_FIM_AVALIACAO_24H";
    private final EstudioRepository estudios;
    private final UsuarioRepository usuarios;
    private final Clock clock;
    private final int trialDays;

    public AssinaturaAvisoFimRenderer(EstudioRepository estudios, UsuarioRepository usuarios, Clock clock,
            @Value("$" + "{billing.trial-days:14}") int trialDays) {
        this.estudios = estudios;
        this.usuarios = usuarios;
        this.clock = clock;
        this.trialDays = trialDays;
    }

    @Override
    public boolean suporta(String tipo) {
        return TIPO.equals(tipo);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean obsoleto(EmailOutboxJob job) {
        try {
            var estudio = estudios.findById(job.estudioId()).orElse(null);
            if (estudio == null || estudio.isEncerrado()) return true;
            LocalDateTime fimAvaliacao = LocalDateTime.parse(job.referencia());
            Instant agora = clock.instant();
            Instant fim = fimAvaliacao.atZone(clock.getZone()).toInstant();
            if (!estudio.getCriadoEm().plusDays(trialDays).equals(fimAvaliacao)
                    || agora.isBefore(fim.minus(Duration.ofHours(24))) || !agora.isBefore(fim)
                    || (estudio.getPagoAte() != null && estudio.getPagoAte().isAfter(LocalDateTime.now(clock)))) return true;
            return usuarios.findByEstudioIdOrderByNomeAsc(estudio.getId()).stream()
                    .filter(Usuario::isAtivo)
                    .filter(usuario -> usuario.getPerfil() == Perfil.PROPRIETARIO)
                    .map(Usuario::getEmail)
                    .filter(email -> email != null && !email.isBlank())
                    .noneMatch(email -> email.equalsIgnoreCase(job.destinatario()));
        } catch (RuntimeException invalidReference) {
            return true;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public EmailOutboxConteudo render(EmailOutboxJob job) {
        LocalDateTime fimAvaliacao = LocalDateTime.parse(job.referencia());
        String dataHora = fimAvaliacao.format(DateTimeFormatter.ofPattern("dd/MM/yyyy '\u00e0s' HH:mm", Locale.forLanguageTag("pt-BR")));
        String texto = "A avalia\u00e7\u00e3o gratuita do seu sal\u00e3o no Marca\u00ea termina em " + dataHora + " (" + clock.getZone().getId() + ").\n\n"
                + "Para evitar a pausa de novas grava\u00e7\u00f5es, abra Assinatura na sua conta e escolha Pix avulso ou cart\u00e3o recorrente. "
                + "O Pix exige um novo pagamento a cada ciclo; o cart\u00e3o \u00e9 recorrente.\n\n"
                + "Nenhum meio \u00e9 cobrado sem sua escolha. Se n\u00e3o houver pagamento confirmado at\u00e9 o prazo, a conta permanece "
                + "dispon\u00edvel para consulta e as grava\u00e7\u00f5es voltam ap\u00f3s a confirma\u00e7\u00e3o do pagamento pela Stripe.";
        return new EmailOutboxConteudo("Sua avalia\u00e7\u00e3o Marca\u00ea est\u00e1 perto do fim", texto);
    }
}
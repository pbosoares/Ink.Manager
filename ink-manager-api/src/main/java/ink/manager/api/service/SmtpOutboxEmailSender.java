package ink.manager.api.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SmtpOutboxEmailSender implements EmailSender {
    private final ObjectProvider<JavaMailSender> sender;
    private final boolean enabled;
    private final String host;
    private final String username;
    private final String password;
    private final String from;

    public SmtpOutboxEmailSender(ObjectProvider<JavaMailSender> sender,
            @Value("${MAIL_ENABLED:false}") boolean enabled,
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${app.email-from:}") String from) {
        this.sender = sender;
        this.enabled = enabled;
        this.host = host;
        this.username = username;
        this.password = password;
        this.from = from;
    }

    @Override
    public boolean disponivel() {
        return enabled && !host.isBlank() && !username.isBlank() && !password.isBlank()
            && !from.isBlank() && sender.getIfAvailable() != null;
    }

    @Override
    public void enviar(String destinatario, EmailOutboxConteudo conteudo) {
        if (!disponivel()) throw new IllegalStateException("EMAIL_SENDER_DISABLED");
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(destinatario);
        message.setSubject(conteudo.assunto());
        message.setText(conteudo.texto());
        sender.getObject().send(message);
    }
}

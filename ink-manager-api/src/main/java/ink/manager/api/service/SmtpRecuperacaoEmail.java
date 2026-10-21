package ink.manager.api.service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
@Service
public class SmtpRecuperacaoEmail implements RecuperacaoEmail {
    private final ObjectProvider<JavaMailSender> sender;
    private final boolean enabled;
    private final String host;
    private final String username;
    private final String password;
    private final String from;
    public SmtpRecuperacaoEmail(ObjectProvider<JavaMailSender> sender,
            @Value("${MAIL_ENABLED:false}") boolean enabled,
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${app.email-from:}") String from){
        this.sender=sender;this.enabled=enabled;this.host=host;this.username=username;this.password=password;this.from=from;
    }
    public boolean disponivel(){return enabled&&!host.isBlank()&&!username.isBlank()&&!password.isBlank()&&!from.isBlank()&&sender.getIfAvailable()!=null;}
    public void enviar(String email,String link){
        if(!disponivel())throw new IllegalStateException("EMAIL_DISABLED_OR_UNCONFIGURED");
        var message=new SimpleMailMessage();message.setFrom(from);message.setTo(email);
        message.setSubject("Marcaê - redefinir senha");
        message.setText("Use o link para redefinir sua senha. Ele vale por 30 minutos e pode ser usado uma vez.\n\n"+link+"\n\nSe voce nao solicitou, ignore esta mensagem.");
        sender.getObject().send(message);
    }
}

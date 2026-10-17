package ink.manager.api.service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
@Service
public class SmtpRecuperacaoEmail implements RecuperacaoEmail {
    private final ObjectProvider<JavaMailSender> sender;
    private final String host;
    private final String from;
    public SmtpRecuperacaoEmail(ObjectProvider<JavaMailSender> sender,@Value("${spring.mail.host:}") String host,@Value("${app.email-from:}") String from){this.sender=sender;this.host=host;this.from=from;}
    public boolean disponivel(){return !host.isBlank()&&!from.isBlank()&&sender.getIfAvailable()!=null;}
    public void enviar(String email,String link){
        var message=new SimpleMailMessage();message.setFrom(from);message.setTo(email);
        message.setSubject("Ink Manager - redefinir senha");
        message.setText("Use o link para redefinir sua senha. Ele vale por 30 minutos e pode ser usado uma vez.\n\n"+link+"\n\nSe voce nao solicitou, ignore esta mensagem.");
        sender.getObject().send(message);
    }
}

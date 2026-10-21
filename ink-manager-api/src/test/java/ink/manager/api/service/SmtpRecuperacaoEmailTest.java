package ink.manager.api.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpRecuperacaoEmailTest {
    @Test
    @SuppressWarnings("unchecked")
    void flagDesligadaBloqueiaEnvioMesmoComCredenciais() {
        ObjectProvider<JavaMailSender> provider=mock(ObjectProvider.class);
        var email=new SmtpRecuperacaoEmail(provider,false,"smtp.example.test","user","secret","no-reply@example.test");

        assertFalse(email.disponivel());
        assertThrows(IllegalStateException.class,()->email.enviar("person@example.test","https://example.test/reset"));
        verifyNoInteractions(provider);
    }

    @Test
    @SuppressWarnings("unchecked")
    void credenciaisAusentesMantemEnvioIndisponivelMesmoComFlagLigada() {
        ObjectProvider<JavaMailSender> provider=mock(ObjectProvider.class);
        var email=new SmtpRecuperacaoEmail(provider,true,"smtp.example.test","user","","no-reply@example.test");

        assertFalse(email.disponivel());
        assertThrows(IllegalStateException.class,()->email.enviar("person@example.test","https://example.test/reset"));
        verifyNoInteractions(provider);
    }

    @Test
    @SuppressWarnings("unchecked")
    void envioPermitidoComFlagECredenciaisEnviaMensagem() {
        ObjectProvider<JavaMailSender> provider=mock(ObjectProvider.class);
        JavaMailSender mail=mock(JavaMailSender.class);
        when(provider.getIfAvailable()).thenReturn(mail);
        when(provider.getObject()).thenReturn(mail);
        var email=new SmtpRecuperacaoEmail(provider,true,"smtp.example.test","user","secret","no-reply@example.test");

        assertTrue(email.disponivel());
        email.enviar("person@example.test","https://example.test/reset");

        var message=org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        assertEquals("no-reply@example.test",message.getValue().getFrom());
        assertArrayEquals(new String[]{"person@example.test"},message.getValue().getTo());
        assertTrue(message.getValue().getText().contains("https://example.test/reset"));
    }
}

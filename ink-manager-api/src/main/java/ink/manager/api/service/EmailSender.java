package ink.manager.api.service;

public interface EmailSender {
    boolean disponivel();
    void enviar(String destinatario, EmailOutboxConteudo conteudo);
}

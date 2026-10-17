package ink.manager.api.service;
public interface RecuperacaoEmail {
    boolean disponivel();
    void enviar(String email,String link);
}

package ink.manager.api.service;

public interface EmailOutboxRenderer {
    boolean suporta(String tipo);
    /** True when a claimed job no longer represents the current domain state. */
    default boolean obsoleto(EmailOutboxJob job) { return false; }
    EmailOutboxConteudo render(EmailOutboxJob job);
    default void falhouDefinitivamente(EmailOutboxJob job) {}
}

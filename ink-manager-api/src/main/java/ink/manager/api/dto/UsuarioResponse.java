package ink.manager.api.dto;
import ink.manager.api.model.*;
public record UsuarioResponse(Long id, String nome, String email, Perfil perfil, boolean ativo, Long version) {
    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getPerfil(), usuario.isAtivo(), usuario.getVersion());
    }
}

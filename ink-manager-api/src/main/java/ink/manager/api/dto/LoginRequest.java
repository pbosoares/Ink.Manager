package ink.manager.api.dto;

public record LoginRequest(
    String email,
    String senha
) {
}
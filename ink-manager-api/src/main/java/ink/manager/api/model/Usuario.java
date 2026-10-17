package ink.manager.api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.ManyToOne(optional = false)
    @jakarta.persistence.JoinColumn(name = "estudio_id", nullable = false)
    private Estudio estudio;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Perfil perfil = Perfil.FUNCIONARIO;
    private boolean ativo = true;
    private long tokenVersion;
    @Version private Long version;
    public Perfil getPerfil() { return perfil; }
    public void setPerfil(Perfil perfil) { this.perfil = perfil; }
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    public long getTokenVersion() { return tokenVersion; }
    public void revogarSessoes() { this.tokenVersion++; }
    public Long getVersion() { return version; }

    public Estudio getEstudio() { return estudio; }
    public void setEstudio(Estudio estudio) { this.estudio = estudio; }


    @Column(nullable = false)
    @NotBlank(message = "Nome é obrigatório")
    private String nome;

    @Column(nullable = false, unique = true)
    @NotBlank(message = "E-mail é obrigatório")
    @Email(message = "E-mail inválido")
    private String email;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(nullable = false)
    @NotBlank(message = "Senha é obrigatória")
    @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres")
    private String senha;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }
}

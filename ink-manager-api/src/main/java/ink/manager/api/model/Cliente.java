package ink.manager.api.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Entity
@jakarta.persistence.Table(name = "cliente")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @jakarta.persistence.ManyToOne(optional = false)
    @jakarta.persistence.JoinColumn(name = "estudio_id", nullable = false)
    private Estudio estudio;
    public Estudio getEstudio() { return estudio; }
    public void setEstudio(Estudio estudio) { this.estudio = estudio; }


    @jakarta.persistence.Version
    private Long version;
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }


    @NotBlank(message = "Nome é obrigatório")
    @jakarta.validation.constraints.Size(max = 255)
    private String nome;

    @NotBlank(message = "Telefone é obrigatório")
    @jakarta.validation.constraints.Size(max = 30)
    private String telefone;

    @Min(value = 0, message = "Idade não pode ser negativa")
    @Max(value = 150, message = "Idade inválida")
    private Integer idade;

    @jakarta.validation.constraints.Size(max = 100)
    private String instagram;
    @jakarta.persistence.Column(length=254)
    @jakarta.validation.constraints.Email
    private String email;
    public String getEmail(){return email;}
    public void setEmail(String value){email=value;}

    public Cliente() {
    }

    public Cliente(String nome, String telefone, Integer idade, String instagram) {
    this.nome = nome;
    this.telefone = telefone;
    this.idade = idade;
    this.instagram = instagram;
}

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

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public Integer getIdade() {
    return idade;
}

public void setIdade(Integer idade) {
    this.idade = idade;
}

    public String getInstagram() {
        return instagram;
    }

    public void setInstagram(String instagram) {
        this.instagram = instagram;
    }
}

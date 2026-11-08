package ink.manager.api.model;

import jakarta.persistence.*;

@Entity
@Table(name = "servicos_profissionais", uniqueConstraints = @UniqueConstraint(
        name = "uq_servicos_profissionais_vinculo",
        columnNames = {"estudio_id", "servico_id", "profissional_id"}))
public class ServicoProfissional {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "estudio_id", nullable = false)
    private Long estudioId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servico_id", nullable = false)
    private Servico servico;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profissional_id", nullable = false)
    private Profissional profissional;

    protected ServicoProfissional() {}

    public ServicoProfissional(Long estudioId, Servico servico, Profissional profissional) {
        this.estudioId = estudioId;
        this.servico = servico;
        this.profissional = profissional;
    }

    public Long getId() { return id; }
    public Long getEstudioId() { return estudioId; }
    public Servico getServico() { return servico; }
    public Profissional getProfissional() { return profissional; }
}

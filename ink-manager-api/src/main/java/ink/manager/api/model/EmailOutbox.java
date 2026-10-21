package ink.manager.api.model;

import java.time.Instant;
import jakarta.persistence.*;

@Entity
@Table(name = "email_outbox", uniqueConstraints =
    @UniqueConstraint(name = "uq_email_outbox_estudio_chave", columnNames = {"estudio_id", "chave_idempotencia"}))
public class EmailOutbox {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "estudio_id", nullable = false)
    private Long estudioId;
    @Column(nullable = false, length = 254)
    private String destinatario;
    @Column(nullable = false, length = 80)
    private String tipo;
    @Column(name = "chave_idempotencia", nullable = false, length = 180)
    private String chaveIdempotencia;
    @Column(name = "disponivel_em", nullable = false)
    private Instant disponivelEm;
    @Column(nullable = false, length = 255)
    private String referencia;
    @Column(name = "dados_json", nullable = false, length = 8000)
    private String dadosJson;
    @Column(nullable = false, length = 16)
    private String estado = "PENDENTE";
    @Column(nullable = false)
    private int tentativas;
    @Column(name = "lease_ate")
    private Instant leaseAte;
    @Column(name = "lease_token", length = 36)
    private String leaseToken;
    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;
    @Column(name = "enviado_em")
    private Instant enviadoEm;
    @Column(name = "ultimo_erro", length = 80)
    private String ultimoErro;

    protected EmailOutbox() {}

    public EmailOutbox(long estudioId, String destinatario, String tipo, String chaveIdempotencia,
            Instant disponivelEm, String referencia, String dadosJson, Instant criadoEm) {
        this.estudioId = estudioId;
        this.destinatario = destinatario;
        this.tipo = tipo;
        this.chaveIdempotencia = chaveIdempotencia;
        this.disponivelEm = disponivelEm;
        this.referencia = referencia;
        this.dadosJson = dadosJson;
        this.criadoEm = criadoEm;
    }

    public Long getId() { return id; }
    public Long getEstudioId() { return estudioId; }
    public String getDestinatario() { return destinatario; }
    public String getTipo() { return tipo; }
    public String getChaveIdempotencia() { return chaveIdempotencia; }
    public Instant getDisponivelEm() { return disponivelEm; }
    public String getReferencia() { return referencia; }
    public String getDadosJson() { return dadosJson; }
    public String getEstado() { return estado; }
    public int getTentativas() { return tentativas; }
    public Instant getLeaseAte() { return leaseAte; }
    public String getLeaseToken() { return leaseToken; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getEnviadoEm() { return enviadoEm; }
    public String getUltimoErro() { return ultimoErro; }
}

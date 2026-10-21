package ink.manager.api.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "email_action_tokens")
public class EmailActionToken {
    @Id @Column(name = "token_hash", length = 64) private String tokenHash;
    @Column(name = "estudio_id", nullable = false) private Long estudioId;
    @Column(name = "marcacao_id", nullable = false) private Long marcacaoId;
    @Column(nullable = false, length = 16) private String acao;
    @Column(name = "criado_em", nullable = false) private Instant criadoEm;
    @Column(name = "expira_em", nullable = false) private Instant expiraEm;
    @Column(name = "usado_em") private Instant usadoEm;
    @Version private Long version;

    protected EmailActionToken() {}
    public EmailActionToken(String hash, long estudioId, long marcacaoId, String acao, Instant criadoEm, Instant expiraEm) {
        this.tokenHash=hash; this.estudioId=estudioId; this.marcacaoId=marcacaoId; this.acao=acao; this.criadoEm=criadoEm; this.expiraEm=expiraEm;
    }
    public String getTokenHash(){return tokenHash;}
    public Long getEstudioId(){return estudioId;}
    public Long getMarcacaoId(){return marcacaoId;}
    public String getAcao(){return acao;}
    public Instant getExpiraEm(){return expiraEm;}
    public Instant getUsadoEm(){return usadoEm;}
    public void usar(Instant quando){usadoEm=quando;}
}

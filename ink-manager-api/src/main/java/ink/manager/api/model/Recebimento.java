package ink.manager.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name="recebimentos")
public class Recebimento {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="estudio_id",nullable=false) private Estudio estudio;
    @ManyToOne(optional=false) @JoinColumn(name="marcacao_id",nullable=false,unique=true) private Marcacao marcacao;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal valor;
    @Column(nullable=false) private Instant confirmadoEm;
    @Column(nullable=false) private Long confirmadoPor;
    public Recebimento(){}
    public Recebimento(Marcacao m,BigDecimal v,Instant quando,Long usuario){estudio=m.getEstudio();marcacao=m;valor=v;confirmadoEm=quando;confirmadoPor=usuario;}
    public Long getId(){return id;}
    public Marcacao getMarcacao(){return marcacao;}
    public BigDecimal getValor(){return valor;}
    public Instant getConfirmadoEm(){return confirmadoEm;}
}

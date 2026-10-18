package ink.manager.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name="servicos")
public class Servico {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false) @JoinColumn(name="estudio_id",nullable=false) private Estudio estudio;
    @Column(nullable=false,length=100) private String nome;
    @Column(nullable=false,precision=12,scale=2) private BigDecimal preco;
    @Column(nullable=false) private Integer duracaoMinutos;
    @Column(nullable=false) private boolean ativo=true;
    @Version private Long version;
    public Long getId(){return id;}
    public Estudio getEstudio(){return estudio;}
    public void setEstudio(Estudio value){estudio=value;}
    public String getNome(){return nome;}
    public void setNome(String value){nome=value;}
    public BigDecimal getPreco(){return preco;}
    public void setPreco(BigDecimal value){preco=value;}
    public Integer getDuracaoMinutos(){return duracaoMinutos;}
    public void setDuracaoMinutos(Integer value){duracaoMinutos=value;}
    public boolean isAtivo(){return ativo;}
    public void setAtivo(boolean value){ativo=value;}
    public Long getVersion(){return version;}
}

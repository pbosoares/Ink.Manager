package ink.manager.api.service;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AssinaturaStore {
    public record Checkout(String id,long estudioId,String metodo,BigDecimal valor,String providerId,String url,String status,LocalDateTime criadoEm){}
    public record Pagamento(String id,String checkoutId,long estudioId,boolean aprovado,LocalDateTime fim){}
    private final JdbcTemplate db;
    public AssinaturaStore(JdbcTemplate db){this.db=db;}
    private Checkout row(ResultSet r,int n)throws SQLException{return new Checkout(r.getString("id"),r.getLong("estudio_id"),r.getString("metodo"),r.getBigDecimal("valor"),r.getString("provider_id"),r.getString("checkout_url"),r.getString("status"),r.getTimestamp("criado_em").toLocalDateTime());}
    public Optional<Checkout> checkout(String id){return db.query("SELECT * FROM assinatura_checkouts WHERE id=?",this::row,id).stream().findFirst();}
    public List<Checkout> recentes(long estudioId){return db.query("SELECT * FROM assinatura_checkouts WHERE estudio_id=? ORDER BY criado_em DESC FETCH FIRST 20 ROWS ONLY",this::row,estudioId);}
    public void criar(String id,long estudioId,String metodo,BigDecimal valor,LocalDateTime agora){db.update("INSERT INTO assinatura_checkouts(id,estudio_id,metodo,valor,criado_em) VALUES (?,?,?,?,?)",id,estudioId,metodo,valor,Timestamp.valueOf(agora));}
    public void registrar(String id,long estudioId,String providerId,String url){db.update("UPDATE assinatura_checkouts SET provider_id=?,checkout_url=? WHERE id=? AND estudio_id=?",providerId,url,id,estudioId);}
    public void status(String id,long estudioId,String status){db.update("UPDATE assinatura_checkouts SET status=? WHERE id=? AND estudio_id=?",status,id,estudioId);}
    public Optional<Pagamento> pagamento(String id){return db.query("SELECT * FROM assinatura_pagamentos WHERE id=?",(r,n)->new Pagamento(r.getString("id"),r.getString("checkout_id"),r.getLong("estudio_id"),r.getBoolean("aprovado"),r.getTimestamp("fim")==null?null:r.getTimestamp("fim").toLocalDateTime()),id).stream().findFirst();}
    public void gravarPagamento(Pagamento pagamento){
        var fim=pagamento.fim()==null?null:Timestamp.valueOf(pagamento.fim());
        if(pagamento(pagamento.id()).isEmpty())db.update("INSERT INTO assinatura_pagamentos(id,checkout_id,estudio_id,aprovado,fim) VALUES (?,?,?,?,?)",pagamento.id(),pagamento.checkoutId(),pagamento.estudioId(),pagamento.aprovado(),fim);
        else db.update("UPDATE assinatura_pagamentos SET aprovado=?,fim=? WHERE id=? AND checkout_id=? AND estudio_id=?",pagamento.aprovado(),fim,pagamento.id(),pagamento.checkoutId(),pagamento.estudioId());
        var maximo=db.queryForObject("SELECT MAX(fim) FROM assinatura_pagamentos WHERE estudio_id=? AND aprovado=TRUE",Timestamp.class,pagamento.estudioId());
        db.update("UPDATE estudios SET pago_ate=? WHERE id=?",maximo,pagamento.estudioId());
    }
}

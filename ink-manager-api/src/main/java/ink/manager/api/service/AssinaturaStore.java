package ink.manager.api.service;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AssinaturaStore {
    public record Checkout(String id,long estudioId,String metodo,BigDecimal valor,String provedor,String providerId,String subscriptionId,String url,String status,LocalDateTime criadoEm){}
    public record Pagamento(String id,String checkoutId,long estudioId,boolean aprovado,LocalDateTime fim,LocalDateTime aprovadoEm,long providerUpdatedAt){}
    private final JdbcTemplate db;
    public AssinaturaStore(JdbcTemplate db){this.db=db;}
    private Checkout row(ResultSet r,int n)throws SQLException{return new Checkout(r.getString("id"),r.getLong("estudio_id"),r.getString("metodo"),r.getBigDecimal("valor"),r.getString("provedor"),r.getString("provider_id"),r.getString("subscription_id"),r.getString("checkout_url"),r.getString("status"),r.getTimestamp("criado_em").toLocalDateTime());}
    public List<Checkout> paraConciliar(LocalDateTime desde){return db.query("SELECT c.* FROM assinatura_checkouts c JOIN estudios e ON e.id=c.estudio_id AND e.encerrado_em IS NULL WHERE c.provedor IN ('MERCADOPAGO','STRIPE') AND c.provider_id IS NOT NULL AND (c.status IN ('authorized','paused','trialing','active','past_due') OR c.criado_em>=?) ORDER BY c.ultimo_sync_em ASC NULLS FIRST FETCH FIRST 100 ROWS ONLY",this::row,Timestamp.valueOf(desde));}
    public void marcarConciliacao(String id,LocalDateTime agora){db.update("UPDATE assinatura_checkouts SET ultimo_sync_em=? WHERE id=?",Timestamp.valueOf(agora),id);}
    public Optional<Checkout> checkout(String id){return db.query("SELECT * FROM assinatura_checkouts WHERE id=?",this::row,id).stream().findFirst();}
    public Optional<Checkout> aberto(long estudioId,String metodo,String provedor){return db.query("SELECT * FROM assinatura_checkouts WHERE estudio_id=? AND metodo=? AND provedor=? AND status IN ('pending','authorized','paused','trialing','active','past_due') ORDER BY criado_em DESC FETCH FIRST 1 ROWS ONLY",this::row,estudioId,metodo,provedor).stream().findFirst();}
    public Optional<Checkout> checkoutPorSubscription(String subscriptionId){return db.query("SELECT * FROM assinatura_checkouts WHERE provedor='STRIPE' AND subscription_id=?",this::row,subscriptionId).stream().findFirst();}
    public List<Checkout> recentes(long estudioId){return db.query("SELECT * FROM assinatura_checkouts WHERE estudio_id=? ORDER BY criado_em DESC FETCH FIRST 20 ROWS ONLY",this::row,estudioId);}
    public void criar(String id,long estudioId,String metodo,BigDecimal valor,String provedor,LocalDateTime agora){db.update("INSERT INTO assinatura_checkouts(id,estudio_id,metodo,valor,provedor,criado_em) VALUES (?,?,?,?,?,?)",id,estudioId,metodo,valor,provedor,Timestamp.valueOf(agora));}
    public void registrar(String id,long estudioId,String providerId,String url){db.update("UPDATE assinatura_checkouts SET provider_id=?,checkout_url=? WHERE id=? AND estudio_id=?",providerId,url,id,estudioId);}
    public void status(String id,long estudioId,String status){db.update("UPDATE assinatura_checkouts SET status=? WHERE id=? AND estudio_id=?",status,id,estudioId);}
    public void stripeSubscription(String id,long estudioId,String subscriptionId,String status,long updatedAt){db.update("UPDATE assinatura_checkouts SET subscription_id=?,status=?,provider_updated_at=? WHERE id=? AND estudio_id=? AND provedor='STRIPE' AND provider_updated_at<=? AND (status<>'cancelled' OR ?='cancelled')",subscriptionId,status,updatedAt,id,estudioId,updatedAt,status);}
    public Optional<Pagamento> pagamento(String id){return db.query("SELECT * FROM assinatura_pagamentos WHERE id=?",(r,n)->new Pagamento(r.getString("id"),r.getString("checkout_id"),r.getLong("estudio_id"),r.getBoolean("aprovado"),r.getTimestamp("fim")==null?null:r.getTimestamp("fim").toLocalDateTime(),r.getTimestamp("aprovado_em")==null?null:r.getTimestamp("aprovado_em").toLocalDateTime(),r.getLong("provider_updated_at")),id).stream().findFirst();}
    public void statusContrato(String id,long estudioId,String status,long updatedAt){
        db.update("UPDATE assinatura_checkouts SET status=?,provider_updated_at=? WHERE id=? AND estudio_id=? AND provider_updated_at<=? AND (status<>'cancelled' OR ?='cancelled')",status,updatedAt,id,estudioId,updatedAt,status);
    }
    public void gravarPagamento(Pagamento pagamento){
        var aprovadoEm=pagamento.aprovadoEm()==null?null:Timestamp.valueOf(pagamento.aprovadoEm());
        if(pagamento(pagamento.id()).isEmpty())db.update("INSERT INTO assinatura_pagamentos(id,checkout_id,estudio_id,aprovado,aprovado_em,provider_updated_at) VALUES (?,?,?,?,?,?)",pagamento.id(),pagamento.checkoutId(),pagamento.estudioId(),pagamento.aprovado(),aprovadoEm,pagamento.providerUpdatedAt());
        else db.update("UPDATE assinatura_pagamentos SET aprovado=?,aprovado_em=?,provider_updated_at=? WHERE id=? AND checkout_id=? AND estudio_id=?",pagamento.aprovado(),aprovadoEm,pagamento.providerUpdatedAt(),pagamento.id(),pagamento.checkoutId(),pagamento.estudioId());
        // Rebuild the paid balance from confirmed payments, including after refunds.
        var pagamentos=db.query("SELECT id,aprovado_em,fim FROM assinatura_pagamentos WHERE estudio_id=? AND aprovado=TRUE ORDER BY aprovado_em ASC NULLS FIRST,id",(rs,n)->new Object[]{rs.getString("id"),rs.getTimestamp("aprovado_em"),rs.getTimestamp("fim")},pagamento.estudioId());
        LocalDateTime saldo=null;
        for(var item:pagamentos){
            var aprovado=(Timestamp)item[1];var anterior=(Timestamp)item[2];
            LocalDateTime inicio=aprovado!=null?aprovado.toLocalDateTime():anterior!=null?anterior.toLocalDateTime().minusMonths(1):null;
            if(inicio==null)continue;
            if(saldo!=null&&saldo.isAfter(inicio))inicio=saldo;
            saldo=inicio.plusMonths(1);
            db.update("UPDATE assinatura_pagamentos SET fim=? WHERE id=?",Timestamp.valueOf(saldo),item[0]);
        }
        db.update("UPDATE estudios SET pago_ate=? WHERE id=?",saldo==null?null:Timestamp.valueOf(saldo),pagamento.estudioId());
    }
}

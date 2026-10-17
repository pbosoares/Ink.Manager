package ink.manager.api.config;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ink.manager.api.exception.ApiException;
@Component
public class MercadoPagoSignature {
    private final String secret;
    public MercadoPagoSignature(@Value("${billing.mp-webhook-secret:}") String secret){this.secret=secret;}
    public void validar(String assinatura,String requestId,String dataId){
        try{
            if(secret.isBlank()||assinatura==null||requestId==null||requestId.isBlank()||dataId==null||!dataId.matches("[A-Za-z0-9_-]{1,100}"))throw new IllegalArgumentException();
            var partes=new HashMap<String,String>();for(String parte:assinatura.split(",")){var item=parte.trim().split("=",2);if(item.length==2)partes.put(item[0],item[1]);}
            String ts=partes.get("ts"),v1=partes.get("v1");if(ts==null||!ts.matches("[0-9]{10,16}")||v1==null)throw new IllegalArgumentException();
            String manifesto="id:"+dataId.toLowerCase(Locale.ROOT)+";request-id:"+requestId+";ts:"+ts+";";
            var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            if(!MessageDigest.isEqual(mac.doFinal(manifesto.getBytes(StandardCharsets.UTF_8)),HexFormat.of().parseHex(v1)))throw new IllegalArgumentException();
        }catch(Exception invalid){throw new ApiException(HttpStatus.UNAUTHORIZED,"Notificacao de pagamento invalida.");}
    }
}

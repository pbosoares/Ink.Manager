package ink.manager.api.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ink.manager.api.exception.ApiException;

@Component
public class StripeWebhookSignature {
    private final String secret;private final Clock clock;
    public StripeWebhookSignature(@Value("${billing.stripe-webhook-secret:}") String secret,Clock clock){this.secret=secret;this.clock=clock;}
    public void validar(String header,String rawBody){
        try{
            if(secret.isBlank()||header==null||rawBody==null||rawBody.length()>1_000_000)throw new IllegalArgumentException();
            String timestamp=null;var signatures=new ArrayList<String>();
            for(String part:header.split(",")){var pair=part.trim().split("=",2);if(pair.length!=2)continue;if(pair[0].equals("t"))timestamp=pair[1];if(pair[0].equals("v1"))signatures.add(pair[1]);}
            if(timestamp==null||!timestamp.matches("[0-9]{10}")||signatures.isEmpty())throw new IllegalArgumentException();
            long seconds=Long.parseLong(timestamp);if(Math.abs(clock.instant().getEpochSecond()-seconds)>300)throw new IllegalArgumentException();
            var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            byte[] expected=mac.doFinal((timestamp+"."+rawBody).getBytes(StandardCharsets.UTF_8));
            for(String signature:signatures){try{if(MessageDigest.isEqual(expected,HexFormat.of().parseHex(signature)))return;}catch(IllegalArgumentException ignored){}}
            throw new IllegalArgumentException();
        }catch(Exception invalid){throw new ApiException(HttpStatus.UNAUTHORIZED,"Notificacao Stripe invalida.");}
    }
}

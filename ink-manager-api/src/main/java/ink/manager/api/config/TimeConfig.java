package ink.manager.api.config;
import java.time.*;
import org.springframework.context.annotation.*;
@Configuration
@org.springframework.scheduling.annotation.EnableScheduling
public class TimeConfig {
 @Bean public Clock studioClock(@org.springframework.beans.factory.annotation.Value("${studio.time-zone:America/Sao_Paulo}") String zone) { return Clock.system(ZoneId.of(zone)); }
}

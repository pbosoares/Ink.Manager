package ink.manager.api.config;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.util.DigestUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cache HTTP do estatico publico (CSS, JS, imagens da landing, favicon, robots e sitemap).
 *
 * <p>Os arquivos nao tem hash no nome e o deploy troca o conteudo no mesmo nome. Por isso:
 * <ul>
 * <li>CSS e JS: {@code no-cache} (o navegador guarda, mas revalida a cada uso). Eles andam junto com
 * o HTML, que nao e cacheado; um JS antigo com HTML novo quebraria o painel logo apos o deploy.
 * A revalidacao custa um 304 sem corpo.</li>
 * <li>Imagens, favicon, robots e sitemap: {@code max-age} curto (1 h) com {@code must-revalidate}.
 * Uma imagem velha por alguns minutos nao quebra nada.</li>
 * </ul>
 * O ETag e o hash do conteudo, entao muda sempre que o arquivo muda, mesmo que a data do JAR seja
 * fixa. HTML e respostas da API continuam com o cabecalho padrao do Spring Security ({@code no-store}).
 */
@Configuration
public class StaticCacheConfig implements WebMvcConfigurer {

    static final CacheControl SCRIPTS_E_ESTILOS = CacheControl.noCache().cachePublic();
    static final CacheControl MIDIA = CacheControl.maxAge(Duration.ofHours(1)).cachePublic().mustRevalidate();

    private final Map<String, String> etags = new ConcurrentHashMap<>();
    private final Function<Resource, String> etagPorConteudo = this::etag;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/*.css", "/*.js")
            .addResourceLocations("classpath:/static/")
            .setCacheControl(SCRIPTS_E_ESTILOS)
            .setEtagGenerator(etagPorConteudo);
        registry.addResourceHandler("/favicon.svg", "/robots.txt", "/sitemap.xml")
            .addResourceLocations("classpath:/static/")
            .setCacheControl(MIDIA)
            .setEtagGenerator(etagPorConteudo);
        registry.addResourceHandler("/landing/**")
            .addResourceLocations("classpath:/static/landing/")
            .setCacheControl(MIDIA)
            .setEtagGenerator(etagPorConteudo);
    }

    private String etag(Resource resource) {
        try {
            String chave = resource.getURL() + "|" + resource.lastModified() + "|" + resource.contentLength();
            return etags.computeIfAbsent(chave, ignorada -> {
                try (InputStream in = resource.getInputStream()) {
                    return DigestUtils.md5DigestAsHex(in);
                } catch (IOException falha) {
                    throw new java.io.UncheckedIOException(falha);
                }
            });
        } catch (IOException | java.io.UncheckedIOException falha) {
            return null;
        }
    }
}

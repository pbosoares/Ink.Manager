package ink.manager.api.config;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;
import org.springframework.web.util.ServletRequestPathUtils;

/**
 * Regra final da autorizacao: exige autenticacao, exceto quando a URL nao existe.
 *
 * <p>Sem esta regra, {@code GET /naoexiste} sem token respondia 401 (o Security barrava antes do
 * despacho). Agora, para pedidos anonimos, verificamos se algum handler da aplicacao (controller,
 * actuator etc.) atende a URL. Se nenhum atende e nao existe arquivo estatico com esse caminho, o
 * pedido segue e o Spring responde 404. Qualquer duvida (handler encontrado, erro na busca, caminho
 * codificado, arquivo estatico existente) mantem a exigencia de autenticacao: rota nova nunca vira
 * publica por acidente, e arquivo estatico continua precisando estar no permitAll.
 */
final class RotaInexistenteAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final AuthorizationDecision PERMITIDO = new AuthorizationDecision(true);

    private final AuthorizationManager<RequestAuthorizationContext> autenticado = AuthenticatedAuthorizationManager.authenticated();
    private final ListableBeanFactory beans;
    private final ResourceLoader resources;
    private final String[] locaisEstaticos;
    private volatile List<HandlerMapping> mapeamentos;

    RotaInexistenteAuthorizationManager(ListableBeanFactory beans, ResourceLoader resources, WebProperties web) {
        this.beans = beans;
        this.resources = resources;
        this.locaisEstaticos = web.getResources().getStaticLocations();
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        AuthorizationResult resultado = autenticado.authorize(authentication, context);
        if (resultado != null && resultado.isGranted()) {
            return resultado;
        }
        return rotaInexistente(context.getRequest()) ? PERMITIDO : resultado;
    }

    boolean rotaInexistente(HttpServletRequest original) {
        String caminho = original.getRequestURI().substring(original.getContextPath().length());
        if (caminho.isEmpty() || caminho.contains("%") || caminho.contains("..") || caminho.contains("\\")) {
            return false;
        }
        HttpServletRequest request = new AtributosIsolados(original);
        try {
            ServletRequestPathUtils.parseAndCache(request);
            for (HandlerMapping mapeamento : mapeamentos()) {
                HandlerExecutionChain cadeia = mapeamento.getHandler(request);
                if (cadeia != null && !(cadeia.getHandler() instanceof ResourceHttpRequestHandler)) {
                    return false;
                }
            }
        } catch (Exception falha) {
            // Ex.: metodo nao suportado numa rota existente. Na duvida, a rota existe.
            return false;
        }
        return !arquivoEstaticoExiste(caminho);
    }

    private boolean arquivoEstaticoExiste(String caminho) {
        String relativo = caminho.startsWith("/") ? caminho.substring(1) : caminho;
        for (String local : locaisEstaticos) {
            String base = local.endsWith("/") ? local : local + "/";
            Resource resource = resources.getResource(base + relativo);
            if (resource.exists()) {
                return true;
            }
        }
        return false;
    }

    private List<HandlerMapping> mapeamentos() {
        List<HandlerMapping> atual = mapeamentos;
        if (atual == null) {
            atual = List.copyOf(beans.getBeansOfType(HandlerMapping.class).values());
            mapeamentos = atual;
        }
        return atual;
    }

    /** Evita que a busca de handler deixe atributos (variaveis de URI, padrao casado) no pedido real. */
    private static final class AtributosIsolados extends HttpServletRequestWrapper {
        private final Map<String, Object> atributos = new HashMap<>();
        private final Set<String> removidos = new HashSet<>();

        AtributosIsolados(HttpServletRequest request) {
            super(request);
        }

        @Override
        public Object getAttribute(String name) {
            if (atributos.containsKey(name)) {
                return atributos.get(name);
            }
            return removidos.contains(name) ? null : super.getAttribute(name);
        }

        @Override
        public void setAttribute(String name, Object value) {
            if (value == null) {
                removeAttribute(name);
                return;
            }
            atributos.put(name, value);
            removidos.remove(name);
        }

        @Override
        public void removeAttribute(String name) {
            atributos.remove(name);
            removidos.add(name);
        }

        @Override
        public Enumeration<String> getAttributeNames() {
            Set<String> nomes = new HashSet<>(Collections.list(super.getAttributeNames()));
            nomes.removeAll(removidos);
            nomes.addAll(atributos.keySet());
            return Collections.enumeration(nomes);
        }
    }
}

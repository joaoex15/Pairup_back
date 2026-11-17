package com.example.pairup.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limite de requisições por IP em janela fixa de 1 minuto, guardado em memória.
 *
 * Duas faixas:
 *  - autenticação (POST /users/login, POST /users): baixa, contra força bruta e criação em massa;
 *  - geral: alta, só para conter abuso (scraping do feed, spam de likes, uploads em loop).
 *
 * O IP vem de request.getRemoteAddr(): com server.forward-headers-strategy=native o Tomcat
 * já o resolve a partir do X-Forwarded-For confiável do proxy, então o cliente não o forja.
 * Em memória vale por instância; com várias réplicas, trocar por Redis/bucket4j.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final long JANELA_MS = 60_000L;
    private static final int MAX_CHAVES = 100_000;

    private final int limiteAuth;
    private final int limiteGeral;
    private final boolean habilitado;

    private final ConcurrentHashMap<String, Janela> contadores = new ConcurrentHashMap<>();

    public RateLimitFilter(@Value("${seguranca.rate-limit.auth-por-minuto:10}") int limiteAuth,
                           @Value("${seguranca.rate-limit.geral-por-minuto:300}") int limiteGeral,
                           @Value("${seguranca.rate-limit.habilitado:true}") boolean habilitado) {
        this.limiteAuth = limiteAuth;
        this.limiteGeral = limiteGeral;
        this.habilitado = habilitado;
    }

    private static final class Janela {
        final long inicio;
        final AtomicInteger contagem = new AtomicInteger();
        Janela(long inicio) { this.inicio = inicio; }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!habilitado || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        boolean rotaAuth = "POST".equalsIgnoreCase(request.getMethod())
                && ("/users/login".equals(request.getRequestURI()) || "/users".equals(request.getRequestURI()));

        String ip = request.getRemoteAddr();
        String chave = (rotaAuth ? "auth:" : "geral:") + ip;
        int limite = rotaAuth ? limiteAuth : limiteGeral;

        if (excedeu(chave, limite)) {
            log.warn("[SECURITY] Rate limit excedido ({}) para ip={}", rotaAuth ? "auth" : "geral", ip);
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"erro\": \"Muitas requisições. Aguarde um minuto e tente de novo.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean excedeu(String chave, int limite) {
        long agora = System.currentTimeMillis();
        if (contadores.size() > MAX_CHAVES) limparExpiradas(agora);

        Janela janela = contadores.compute(chave, (k, atual) ->
                atual == null || agora - atual.inicio >= JANELA_MS ? new Janela(agora) : atual);
        return janela.contagem.incrementAndGet() > limite;
    }

    private void limparExpiradas(long agora) {
        contadores.entrySet().removeIf(e -> agora - e.getValue().inicio >= JANELA_MS);
    }
}
